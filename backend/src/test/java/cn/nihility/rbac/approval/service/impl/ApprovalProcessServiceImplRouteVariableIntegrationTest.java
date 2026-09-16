package cn.nihility.rbac.approval.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.approval.service.ApprovalProcessService;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.constant.FormFieldStatus;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import cn.nihility.rbac.formfield.mapper.FormFieldDefinitionMapper;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.BindingStatus;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;
import cn.nihility.rbac.workflow.designer.dto.ApprovalNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.ConditionNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.EdgeConditionDsl;
import cn.nihility.rbac.workflow.designer.dto.EdgeDsl;
import cn.nihility.rbac.workflow.designer.dto.EndNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelDsl;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelVO;
import cn.nihility.rbac.workflow.designer.dto.PublishResultVO;
import cn.nihility.rbac.workflow.designer.dto.StartNodeDsl;
import cn.nihility.rbac.workflow.designer.service.WorkflowProcessModelService;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.entity.ProcessBindingEntity;
import cn.nihility.rbac.workflow.mapper.ProcessBindingMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ApprovalProcessServiceImpl#start} 按流程定义路由字段清单构建 Flowable 流程变量的
 * 真实数据库 + 真实 Flowable 引擎集成测试（workflow-condition-payload-fields change tasks.md
 * 5.3）：真实发布一个含条件分支的 v1 流程模型（条件引用 ORG 业务类型的一个数字框字段），
 * 真实驱动一次流程实例启动，断言最终落在哪个节点，而不是用 mock 断言"应该会路由对"。覆盖
 * 命中条件走对应分支、业务类型不匹配走默认分支、字段值缺失走默认分支三个场景
 * （design.md Decision 2/5，spec.md"按提交的业务表单字段值路由到正确分支"等 Scenario）。
 */
@SpringBootTest
@Transactional
class ApprovalProcessServiceImplRouteVariableIntegrationTest {

    /** 主数据审批流程接口，内部按流程定义的路由字段清单构建 Flowable 流程变量。 */
    @Autowired
    private ApprovalProcessService approvalProcessService;

    /** 流程模型草稿/发布生命周期业务逻辑接口，真实编译 + 部署本测试的条件分支流程。 */
    @Autowired
    private WorkflowProcessModelService workflowProcessModelService;

    /** 业务绑定数据访问接口，直接插入测试专用绑定，绕开管理页面。 */
    @Autowired
    private ProcessBindingMapper processBindingMapper;

    /** 表单字段定义数据访问接口，直接插入测试专用字段定义。 */
    @Autowired
    private FormFieldDefinitionMapper formFieldDefinitionMapper;

    /** 元数据字段数据访问接口，用于查出 ORG 业务类型下预置的 ext1 扩展字段 id 作为绑定目标。 */
    @Autowired
    private MetadataFieldMapper metadataFieldMapper;

    /** 测试专用流程编码/操作类型自增序号，避免多个测试方法之间相互冲突。 */
    private static final AtomicInteger SEQ = new AtomicInteger();

    /** 本测试类使用的条件字段所属业务类型：ORG。 */
    private static final String FIELD_BIZ_TYPE = "ORG";

    /** 本测试类使用的条件字段标识：绑定到 ORG 预置的 ext1 扩展元数据字段。 */
    private static final String FIELD_CODE = "ext1";

    /** 本次测试发布的流程定义 id。 */
    private Long definitionId;

    /**
     * 发布一个含条件分支的流程模型：条件为 {@code ORG.ext1 > 1000}（数字框），命中走
     * {@code highBranch}，否则走默认分支 {@code lowBranch}；同时插入 ext1 的表单字段定义
     * （ORG 预置的元数据字段默认没有表单字段定义，需要测试自行绑定）。
     */
    @BeforeEach
    void setUp() {
        insertNumberFieldDefinition();
        definitionId = publishConditionBranchModel();
    }

    /** 命中条件（提交的字段值满足条件）时应路由到条件分支节点，而非默认分支。 */
    @Test
    void start_shouldRouteToConditionBranch_whenFieldValueMatches() {
        String operationType = "RT_HIT_" + SEQ.incrementAndGet();
        bindGlobal(FIELD_BIZ_TYPE, operationType, definitionId);

        WorkflowInstanceResult result = approvalProcessService.start(
                9001L, FIELD_BIZ_TYPE, operationType, 1L, null, Map.of(FIELD_CODE, 5000));

        assertThat(result.currentNodeId()).isEqualTo("highBranch");
        approvalProcessService.withdraw(result.processInstanceId(), 1L);
    }

    /** 提交的业务类型与条件字段所属业务类型不一致时，应视为条件不满足，路由到默认分支。 */
    @Test
    void start_shouldRouteToDefaultBranch_whenBizTypeMismatches() {
        String operationType = "RT_MISM_" + SEQ.incrementAndGet();
        String submittedBizType = "USER";
        bindGlobal(submittedBizType, operationType, definitionId);

        WorkflowInstanceResult result = approvalProcessService.start(
                9002L, submittedBizType, operationType, 1L, null, Map.of(FIELD_CODE, 5000));

        assertThat(result.currentNodeId()).isEqualTo("lowBranch");
        approvalProcessService.withdraw(result.processInstanceId(), 1L);
    }

    /** 提交内容里没有该条件字段的值时，应视为条件不满足，路由到默认分支。 */
    @Test
    void start_shouldRouteToDefaultBranch_whenFieldMissingFromPayload() {
        String operationType = "RT_MISS_" + SEQ.incrementAndGet();
        bindGlobal(FIELD_BIZ_TYPE, operationType, definitionId);

        WorkflowInstanceResult result = approvalProcessService.start(
                9003L, FIELD_BIZ_TYPE, operationType, 1L, null, Map.of());

        assertThat(result.currentNodeId()).isEqualTo("lowBranch");
        approvalProcessService.withdraw(result.processInstanceId(), 1L);
    }

    /**
     * 把 ORG 预置的 {@code ext1} 元数据字段绑定为一个数字框表单字段定义（默认没有预置的表单
     * 字段定义，需测试自行绑定），供条件校验器/编译器/运行时变量构建统一查询。
     */
    private void insertNumberFieldDefinition() {
        MetadataFieldEntity metadataField = metadataFieldMapper.selectOne(new LambdaQueryWrapper<MetadataFieldEntity>()
                .eq(MetadataFieldEntity::getBizType, FIELD_BIZ_TYPE)
                .eq(MetadataFieldEntity::getColumnName, FIELD_CODE));
        LocalDateTime now = LocalDateTime.now();
        FormFieldDefinitionEntity definition = FormFieldDefinitionEntity.builder()
                .bizType(FIELD_BIZ_TYPE)
                .metadataFieldId(metadataField.getId())
                .fieldName("路由测试数字字段")
                .fieldCode(FIELD_CODE)
                .controlType(FormFieldControlType.NUMBER)
                .isUnique(false)
                .isRequired(false)
                .showInList(true)
                .showInCreate(true)
                .showInEdit(true)
                .showInExport(false)
                .editable(true)
                .showOrder(1)
                .status(FormFieldStatus.ENABLED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        formFieldDefinitionMapper.insert(definition);
    }

    /**
     * 创建 -> 保存草稿 -> 发布一个含条件分支的 v1 流程模型：网关的两条出边分别是携带条件的
     * {@code highBranch} 与不携带条件的默认分支 {@code lowBranch}，两个审批节点均固定指派给
     * 用户 id 1（本测试的发起人本人），并允许自审，避免因候选人为空触发管理员兜底而干扰断言。
     *
     * @return 发布产生的流程定义 id
     */
    private Long publishConditionBranchModel() {
        String processCode = "ROUTE_TEST_PROCESS_" + SEQ.incrementAndGet();
        ProcessModelVO model = workflowProcessModelService.createModel(processCode, "路由字段测试流程", 1L);

        ProcessModelDsl dsl = ProcessModelDsl.builder()
                .processCode(processCode)
                .processName("路由字段测试流程")
                .nodes(List.of(
                        startNode("start"),
                        conditionNode("gateway"),
                        approvalNode("highBranch", "高值分支"),
                        approvalNode("lowBranch", "默认分支"),
                        endNode("end")))
                .edges(List.of(
                        EdgeDsl.builder().from("start").to("gateway").build(),
                        EdgeDsl.builder().from("gateway").to("highBranch")
                                .condition(EdgeConditionDsl.builder()
                                        .fieldBizType(FIELD_BIZ_TYPE).field(FIELD_CODE).operator("GT").value(1000)
                                        .build())
                                .build(),
                        EdgeDsl.builder().from("gateway").to("lowBranch").build(),
                        EdgeDsl.builder().from("highBranch").to("end").build(),
                        EdgeDsl.builder().from("lowBranch").to("end").build()))
                .build();

        workflowProcessModelService.saveDraft(model.getId(), JacksonUtils.toJson(dsl), null);
        PublishResultVO published = workflowProcessModelService.publish(model.getId(), 1L);
        return published.getProcessDefinitionId();
    }

    /** 插入一条 {@code scope_type=GLOBAL} 的测试专用业务绑定，指向本测试发布的流程定义。 */
    private void bindGlobal(String bizType, String operationType, Long definitionId) {
        LocalDateTime now = LocalDateTime.now();
        processBindingMapper.insert(ProcessBindingEntity.builder()
                .bizType(bizType)
                .operationType(operationType)
                .scopeType("GLOBAL")
                .scopeId(0L)
                .definitionId(definitionId)
                .executionMode("LEGACY_SYNC")
                .revision(1L)
                .status(BindingStatus.ENABLED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());
    }

    private StartNodeDsl startNode(String id) {
        StartNodeDsl node = new StartNodeDsl();
        node.setId(id);
        node.setType("START");
        return node;
    }

    private EndNodeDsl endNode(String id) {
        EndNodeDsl node = new EndNodeDsl();
        node.setId(id);
        node.setType("END");
        return node;
    }

    private ConditionNodeDsl conditionNode(String id) {
        ConditionNodeDsl node = new ConditionNodeDsl();
        node.setId(id);
        node.setType("CONDITION");
        return node;
    }

    private ApprovalNodeDsl approvalNode(String id, String name) {
        ApprovalNodeDsl node = new ApprovalNodeDsl();
        node.setId(id);
        node.setType("APPROVAL");
        node.setName(name);
        node.setAssigneeType(AssigneeType.USER);
        node.setAssigneeValue("1");
        node.setApprovalMode(ApprovalMode.SINGLE);
        node.setEmptyAssigneeStrategy(EmptyAssigneeStrategy.TO_WORKFLOW_ADMIN);
        node.setAllowSelfApproval(true);
        node.setAllowTransfer(false);
        node.setAllowDelegate(false);
        node.setAllowAddSign(false);
        node.setAllowReturn(false);
        return node;
    }
}
