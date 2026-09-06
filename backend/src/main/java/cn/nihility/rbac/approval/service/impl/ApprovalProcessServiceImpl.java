package cn.nihility.rbac.approval.service.impl;

import cn.nihility.rbac.approval.service.ApprovalProcessService;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.dto.FormFieldRenderItemVO;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.formfield.support.FormFieldValueConverter;
import cn.nihility.rbac.workflow.designer.dto.RouteFieldCode;
import cn.nihility.rbac.workflow.dslv2.binding.ProcessBindingResolutionService;
import cn.nihility.rbac.workflow.dslv2.binding.ResolvedProcessBinding;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.RejectCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WithdrawCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 基于通用审批引擎 {@link WorkflowService} 的主数据审批流程操作实现，业务代码之外不再直接
 * 依赖 Flowable 的 {@code RuntimeService}/{@code TaskService}（workflow-approval-engine change
 * design.md Decision 8）。{@link #start} 不再硬编码固定的
 * {@code MASTER_DATA_APPROVAL_PROCESS_CODE}，改为经 {@link ProcessBindingResolutionService}
 * 按 {@code (bizType, operationType, applicantOrgId)} 解析实际生效的业务绑定
 * （production-approval-lifecycle change design.md Decision 4，tasks.md 4.5）；同时按流程
 * 定义发布时落库的路由字段清单，从本次提交的表单数据中取值构建 Flowable 流程变量，供条件
 * 分支按提交内容真正路由（workflow-condition-payload-fields change design.md Decision 2）。
 */
@Service
@RequiredArgsConstructor
public class ApprovalProcessServiceImpl implements ApprovalProcessService {

    /** {@code tab_wf_process_definition.route_field_codes} 反序列化的目标类型：
     *  {@code {bizType, fieldCode}} 列表。 */
    private static final TypeReference<List<RouteFieldCode>> ROUTE_FIELD_CODES_TYPE_REFERENCE = new TypeReference<>() {
    };

    /** 通用审批引擎接口。 */
    private final WorkflowService workflowService;

    /** 业务绑定解析服务，负责加锁解析绑定并校验模型/绑定/执行模式是否允许发起。 */
    private final ProcessBindingResolutionService processBindingResolutionService;

    /** 表单字段定义业务逻辑接口，按路由字段的 {@code controlType} 转换提交值
     *  （workflow-condition-payload-fields change design.md Decision 3）。 */
    private final FormFieldDefinitionService formFieldDefinitionService;

    /**
     * {@inheritDoc}
     * <p>
     * 绑定解析（含 {@code SELECT ... FOR UPDATE} 行锁）与流程实例创建须处于同一事务边界，
     * 本方法显式声明 {@code @Transactional}，与
     * {@link ProcessBindingResolutionService#resolveForStart}、
     * {@link WorkflowService#start} 各自的 {@code Propagation.REQUIRED} 共同保证三步在同一
     * 事务内完成（design.md Decision 4"启动时事务内读取并锁定所选绑定...再按 Flowable
     * definitionId 启动"）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public WorkflowInstanceResult start(
            Long requestId, String bizType, String operationType, Long applicantId, Long applicantOrgId,
            Object typedPayload) {
        ResolvedProcessBinding resolved = processBindingResolutionService.resolveForStart(
                bizType, operationType, applicantOrgId);
        Map<String, Object> variables = buildRouteVariables(resolved.definition(), bizType, typedPayload);
        return workflowService.start(new StartProcessCommand(
                resolved.definition().getProcessCode(),
                bizType,
                requestId,
                "主数据变更审批申请#" + requestId,
                applicantId,
                applicantOrgId,
                variables,
                null,
                resolved.definition().getId(),
                resolved.binding().getId(),
                resolved.binding().getRevision(),
                resolved.binding().getExecutionMode()));
    }

    /**
     * 按流程定义发布时落库的路由字段清单，从本次提交的表单数据中取值构建 Flowable 流程变量。
     * 路由字段所属业务对象类型与本次提交的 {@code bizType} 不一致，或字段在本次提交内容中
     * 缺失时，变量值为 {@code null}，交由编译期生成的 null 安全表达式判定条件不满足
     * （workflow-condition-payload-fields change design.md Decision 2/5）。流程定义没有声明
     * 任何路由字段（历史版本/无条件分支）时返回 {@code null}，不设置任何条件相关变量。
     */
    private Map<String, Object> buildRouteVariables(ProcessDefinitionEntity definition, String bizType, Object typedPayload) {
        String routeFieldCodesJson = definition.getRouteFieldCodes();
        if (!StringUtils.hasText(routeFieldCodesJson)) {
            return null;
        }
        List<RouteFieldCode> routeFieldCodes = JacksonUtils.toObj(routeFieldCodesJson, ROUTE_FIELD_CODES_TYPE_REFERENCE);
        if (routeFieldCodes.isEmpty()) {
            return null;
        }

        Map<String, Object> payloadMap = typedPayload == null
                ? Map.of()
                : JacksonUtils.convert(typedPayload, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);

        Map<String, Object> variables = new LinkedHashMap<>();
        for (RouteFieldCode routeFieldCode : routeFieldCodes) {
            String variableName = routeFieldCode.bizType() + "_" + routeFieldCode.fieldCode();
            if (!Objects.equals(routeFieldCode.bizType(), bizType)) {
                variables.put(variableName, null);
                continue;
            }
            Object rawValue = payloadMap.get(routeFieldCode.fieldCode());
            variables.put(variableName, convertRouteValue(bizType, routeFieldCode.fieldCode(), rawValue));
        }
        return variables;
    }

    /**
     * 按字段 {@code controlType} 把提交的原始值转换为 Flowable 流程变量值：数字框转
     * {@link java.math.BigDecimal}，日期转 epoch day（{@code long}），文本框/字典下拉保持
     * 字符串。原始值为空，或该字段已不存在于当前启用的表单字段定义中（定义发布后被停用/
     * 删除），一律返回 {@code null}。
     */
    private Object convertRouteValue(String bizType, String fieldCode, Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        Integer controlType = formFieldDefinitionService.buildRenderSchema(bizType).stream()
                .filter(item -> fieldCode.equals(item.getFieldCode()))
                .map(FormFieldRenderItemVO::getControlType)
                .findFirst()
                .orElse(null);
        if (controlType == null) {
            return null;
        }
        if (Objects.equals(controlType, FormFieldControlType.NUMBER)) {
            return FormFieldValueConverter.toBigDecimal(rawValue);
        }
        if (Objects.equals(controlType, FormFieldControlType.DATE)) {
            return FormFieldValueConverter.toEpochDay(rawValue);
        }
        return String.valueOf(rawValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void approve(Long taskId, Long approverId, String opinion) {
        workflowService.approve(new ApproveCommand(taskId, approverId, opinion, null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reject(Long taskId, Long approverId, String opinion) {
        workflowService.reject(new RejectCommand(taskId, approverId, opinion, null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void withdraw(Long processInstanceId, Long operatorId) {
        workflowService.withdraw(new WithdrawCommand(processInstanceId, operatorId, null, null));
    }
}
