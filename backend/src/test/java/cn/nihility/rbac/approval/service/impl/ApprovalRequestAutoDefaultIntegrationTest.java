package cn.nihility.rbac.approval.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.approval.constant.ApprovalRequestStatus;
import cn.nihility.rbac.approval.mapper.ApprovalRequestMapper;
import cn.nihility.rbac.approval.service.ApprovalRequestService;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.constant.FormFieldStatus;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import cn.nihility.rbac.formfield.mapper.FormFieldDefinitionMapper;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.BindingStatus;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
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
import cn.nihility.rbac.workflow.entity.ProcessBindingEntity;
import cn.nihility.rbac.workflow.mapper.ProcessBindingMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 真实发布 v1 条件模型并提交不满足条件的申请，验证自动兜底、发布快照及审批持久化终态。
 * 测试数据与引擎部署由事务统一回滚。
 */
@SpringBootTest
@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
class ApprovalRequestAutoDefaultIntegrationTest {

    /** 真实审批提交接口。 */
    @Autowired
    private ApprovalRequestService approvalRequestService;

    /** 审批申请状态查询。 */
    @Autowired
    private ApprovalRequestMapper approvalRequestMapper;

    /** 流程定义快照查询。 */
    @Autowired
    private ProcessDefinitionMapper processDefinitionMapper;

    /** 原始草稿查询。 */
    @Autowired
    private ProcessModelMapper processModelMapper;

    /** 流程业务终态查询。 */
    @Autowired
    private ProcessInstanceMapper processInstanceMapper;

    /** 申请人组织任职配置。 */
    @Autowired
    private UserPositionMapper userPositionMapper;

    /** 引擎历史查询，确认实际执行的路径。 */
    @Autowired
    private HistoryService historyService;

    /** 已部署 BPMN 查询。 */
    @Autowired
    private RepositoryService repositoryService;

    /** 流程模型草稿/发布生命周期业务逻辑接口，真实编译 + 部署本测试的条件分支流程。 */
    @Autowired
    private WorkflowProcessModelService workflowProcessModelService;

    /** 业务绑定数据访问接口，直接插入测试专用绑定，绕开管理页面。 */
    @Autowired
    private ProcessBindingMapper processBindingMapper;

    /** 表单字段定义数据访问接口，直接插入测试专用字段定义。 */
    @Autowired
    private FormFieldDefinitionMapper formFieldDefinitionMapper;

    /** 元数据字段数据访问接口，用于查出 USER 业务类型下预置的 ext1 扩展字段 id 作为绑定目标。 */
    @Autowired
    private MetadataFieldMapper metadataFieldMapper;

    /** 测试专用流程编码/操作类型自增序号，避免多个测试方法之间相互冲突。 */
    private static final AtomicInteger SEQ = new AtomicInteger();

    /** 本测试类使用的条件字段所属业务类型：USER。 */
    private static final String FIELD_BIZ_TYPE = "USER";

    /** 本测试类使用的条件字段标识：绑定到 USER 预置的 ext1 扩展元数据字段。 */
    private static final String FIELD_CODE = "ext1";

    /** 本次测试发布的流程定义 id。 */
    private Long definitionId;

    /**
     * 发布一个含条件分支的流程模型：条件为 {@code USER.ext1 > 1000}（数字框），命中走
     * {@code highBranch}，否则走自动生成的默认分支；同时插入 ext1 的表单字段定义
     * （USER 预置的元数据字段默认没有表单字段定义，需要测试自行绑定）。
     */
    @BeforeEach
    void setUp() {
        insertNumberFieldDefinition();
        definitionId = publishConditionBranchModel();
    }

    /** 条件不满足时自动兜底直达结束，无人工任务，审批申请真实落库为已通过。 */
    @Test
    void submit_shouldAutoApproveWithSnapshotMatchingDeployedProcess() {
        long applicantId = 929_800_001L;
        long orgId = -929_800_001L;
        LocalDateTime now = LocalDateTime.now();
        processBindingMapper.insert(ProcessBindingEntity.builder()
                .bizType("USER").operationType("CREATE").scopeType("ORG").scopeId(orgId)
                .definitionId(definitionId).executionMode("LEGACY_SYNC").revision(1L)
                .status(BindingStatus.ENABLED)
                .createBy("test").createTime(now).updateBy("test").updateTime(now).build());
        userPositionMapper.insert(UserPositionEntity.builder()
                .userId(applicantId).orgId(orgId).positionType("primary").showOrder(0)
                .status(PositionStatus.ENABLED)
                .createBy("test").createTime(now).updateBy("test").updateTime(now).build());

        var definition = processDefinitionMapper.selectById(definitionId);
        ProcessModelDsl snapshot = JacksonUtils.toObj(definition.getModelJsonSnapshot(), ProcessModelDsl.class);
        EdgeDsl autoDefault = snapshot.getEdges().stream()
                .filter(edge -> "gateway".equals(edge.getFrom()) && edge.getCondition() == null)
                .findFirst().orElseThrow();
        assertThat(snapshot.getNodes()).hasSize(5).anyMatch(node ->
                node instanceof EndNodeDsl && node.getId().equals(autoDefault.getTo()));
        ProcessModelDsl draft = JacksonUtils.toObj(
                processModelMapper.selectById(definition.getProcessModelId()).getModelJson(), ProcessModelDsl.class);
        assertThat(draft.getNodes()).hasSize(4);
        assertThat(draft.getEdges()).hasSize(3).noneMatch(edge ->
                "gateway".equals(edge.getFrom()) && edge.getCondition() == null);
        var bpmn = repositoryService.getBpmnModel(definition.getFlowableDefinitionId()).getMainProcess();
        var gateway = (ExclusiveGateway) bpmn.getFlowElement("gateway");
        var defaultFlow = (SequenceFlow) bpmn.getFlowElement(gateway.getDefaultFlow());
        assertThat(defaultFlow.getTargetRef()).isEqualTo(autoDefault.getTo());

        UserCreateRequest payload = new UserCreateRequest();
        payload.setName("自动兜底测试用户");
        payload.setCode("AUTO_DEFAULT_" + SEQ.incrementAndGet());
        payload.setExt1("10");
        CurrentUserContext.setUserId(applicantId);
        try {
            var result = approvalRequestService.submit("USER", "CREATE", null, payload);
            var vo = result.getApprovalRequest();
            assertThat(vo.getStatus()).isEqualTo(ApprovalRequestStatus.APPROVED);
            assertThat(vo.getApproverId()).isNull();
            assertThat(vo.getOpinion()).contains("系统自动通过");
            assertThat(vo.getResultTargetId()).isNotNull();
            var persisted = approvalRequestMapper.selectById(vo.getId());
            assertThat(persisted.getStatus()).isEqualTo(ApprovalRequestStatus.APPROVED);
            var instance = processInstanceMapper.selectById(persisted.getProcessInstanceId());
            assertThat(instance.getStatus()).isEqualTo(ProcessInstanceStatus.APPROVED);
            assertThat(instance.getProcessDefinitionId()).isEqualTo(definitionId);
            assertThat(historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(instance.getFlowableInstanceId()).count()).isZero();
            assertThat(historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(instance.getFlowableInstanceId()).activityId(autoDefault.getTo()).count()).isOne();
        } finally {
            CurrentUserContext.clear();
        }
    }

    /**
     * 把 USER 预置的 {@code ext1} 元数据字段绑定为一个数字框表单字段定义（默认没有预置的表单
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

    /** 创建并发布仅包含条件出边的 v1 模型。 */
    private Long publishConditionBranchModel() {
        String processCode = "AUTO_DEFAULT_PROCESS_" + SEQ.incrementAndGet();
        ProcessModelVO model = workflowProcessModelService.createModel(processCode, "路由字段测试流程", 1L);

        ProcessModelDsl dsl = ProcessModelDsl.builder()
                .processCode(processCode)
                .processName("路由字段测试流程")
                .nodes(List.of(
                        startNode("start"),
                        conditionNode("gateway"),
                        approvalNode("highBranch", "高值分支"),
                        endNode("end")))
                .edges(List.of(
                        EdgeDsl.builder().from("start").to("gateway").build(),
                        EdgeDsl.builder().from("gateway").to("highBranch")
                                .condition(EdgeConditionDsl.builder()
                                        .fieldBizType(FIELD_BIZ_TYPE).field(FIELD_CODE).operator("GT").value(1000)
                                        .build())
                                .build(),
                        EdgeDsl.builder().from("highBranch").to("end").build()))
                .build();

        workflowProcessModelService.saveDraft(model.getId(), JacksonUtils.toJson(dsl), null);
        PublishResultVO published = workflowProcessModelService.publish(model.getId(), 1L);
        return published.getProcessDefinitionId();
    }

    /** 创建开始节点。 */
    private StartNodeDsl startNode(String id) {
        StartNodeDsl node = new StartNodeDsl();
        node.setId(id);
        node.setType("START");
        return node;
    }

    /** 创建结束节点。 */
    private EndNodeDsl endNode(String id) {
        EndNodeDsl node = new EndNodeDsl();
        node.setId(id);
        node.setType("END");
        return node;
    }

    /** 创建条件节点。 */
    private ConditionNodeDsl conditionNode(String id) {
        ConditionNodeDsl node = new ConditionNodeDsl();
        node.setId(id);
        node.setType("CONDITION");
        return node;
    }

    /** 创建条件命中后的人工审批节点。 */
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
