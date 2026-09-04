package cn.nihility.rbac.workflow.dto;

import java.util.Map;

/**
 * 启动流程命令对象，{@link cn.nihility.rbac.workflow.engine.WorkflowService#start} 的入参。
 * 是内部服务层参数，不是 HTTP 请求体，不加 {@code jakarta.validation} 注解，校验在
 * Controller 的请求 DTO 上完成。
 * <p>
 * {@code definitionId} 是启动的唯一权威依据（production-approval-lifecycle change design.md
 * Decision 4"启动时事务内读取并锁定所选绑定...再按 Flowable definitionId 启动"）：调用方
 * （通常是 {@code ApprovalProcessServiceImpl.start} 经由
 * {@code ProcessBindingResolutionService} 解析业务绑定得到）负责在构造本命令前完成
 * "校验模型/绑定启用、definition 已发布"等前置校验，{@link cn.nihility.rbac.workflow.engine.flowable.FlowableWorkflowService}
 * 不再像历史实现那样反查 {@code processCode -> processModel.currentDefinitionId}，因此同一
 * {@code processCode} 在不同业务绑定下可以启动不同的历史版本（支持显式回滚）。未经由业务
 * 绑定发起的历史/测试调用方（如直接驱动 {@code WorkflowService} 的引擎集成测试）可以自行
 * 指定 {@code definitionId}，{@code bindingId}/{@code bindingRevision} 置空即可。
 *
 * @param processCode    业务侧流程编码，关联 {@code tab_wf_process_model.process_code}，仅供
 *                       展示/日志使用，不再用于启动时反查流程定义
 * @param businessType   业务对象类型，如 {@code ORG}/{@code USER}/{@code POSITION}/{@code APP}
 * @param businessId     业务对象 id
 * @param title          流程标题，供列表展示
 * @param applicantId    发起人用户 id
 * @param applicantOrgId 发起人所属组织 id，可为空
 * @param variables      附加的流程变量（如条件分支判断字段），可为空
 * @param idempotencyKey 幂等键，取自 {@code X-Request-Id} 请求头，可为空
 * @param definitionId   显式指定启动的流程定义 id（{@code tab_wf_process_definition.id}），
 *                       必填，启动的唯一权威依据
 * @param bindingId      发起时命中的业务绑定 id，非经业务绑定发起时为空
 * @param bindingRevision 发起时命中的业务绑定修订号快照，非经业务绑定发起时为空
 * @param executionMode  发起时使用的执行模式（{@code LEGACY_SYNC}/{@code RELIABLE_ASYNC}），
 *                       非经业务绑定发起时为空，视为历史默认的 {@code LEGACY_SYNC} 语义
 */
public record StartProcessCommand(
        String processCode,
        String businessType,
        Long businessId,
        String title,
        Long applicantId,
        Long applicantOrgId,
        Map<String, Object> variables,
        String idempotencyKey,
        Long definitionId,
        Long bindingId,
        Long bindingRevision,
        String executionMode) {
}
