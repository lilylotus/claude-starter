package cn.nihility.rbac.approval.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.approval.constant.ApprovalOperationType;
import cn.nihility.rbac.approval.constant.ApprovalRequestStatus;
import cn.nihility.rbac.approval.dto.ApprovalRequestVO;
import cn.nihility.rbac.approval.dto.WriteOperationResultVO;
import cn.nihility.rbac.approval.entity.ApprovalRequestEntity;
import cn.nihility.rbac.approval.mapper.ApprovalRequestMapper;
import cn.nihility.rbac.approval.service.ApprovalRequestService;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.designer.compiler.NodeAssigneeRuleDraft;
import cn.nihility.rbac.workflow.dslv2.compiler.CompiledProcessV2;
import cn.nihility.rbac.workflow.dslv2.compiler.WorkflowModelCompilerV2;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EndNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.StartNodeDslV2;
import cn.nihility.rbac.workflow.entity.BusinessLockEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.ProcessBindingEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.BusinessLockMapper;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
import cn.nihility.rbac.workflow.mapper.ProcessBindingMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ApprovalRequestServiceImpl#submit} 零任务流程（命中路径不经过任何审批节点）终态收尾
 * 集成测试（fix-approval-zero-task-process-completion change tasks.md 2.5）：真实数据库 +
 * 真实 Flowable 引擎，覆盖自动通过分支的真实业务写入、自动拒绝分支不写业务数据、正常有开放
 * 任务分支无回归，以及 {@code CurrentUserContext} 在自动通过分支结束后正确恢复为申请人。
 * <p>
 * 为绕开 {@code V1__init_schema.sql} 已为 {@code USER:CREATE} 预置的 {@code scope_type=GLOBAL}
 * 兜底绑定（指向正常需要两级人工审批的 {@code MASTER_DATA_APPROVAL} 流程），本类不直接复用
 * 该全局绑定，而是显式给测试用申请人插入一条指向合成组织 id 的任职记录，令
 * {@code ProcessBindingResolutionService} 按"精确组织优先于全局"的规则命中本类为每个测试
 * 单独插入的 {@code scope_type=ORG} 绑定（指向本类编译部署的零任务流程），从而不影响、也不
 * 依赖既有全局兜底绑定。类上 {@link Transactional}（默认回滚），测试结束后自动清理全部新增
 * 数据，不在共享开发库残留测试痕迹。
 */
@SpringBootTest
@Transactional
class ApprovalRequestServiceImplZeroTaskSubmitIntegrationTest {

    /** 审批申请业务接口。 */
    @Autowired
    private ApprovalRequestService approvalRequestService;

    /** DSL v2 编译器，用于编译"开始 -> 条件节点（仅默认分支）-> 结束"零任务流程。 */
    @Autowired
    private WorkflowModelCompilerV2 compiler;

    /** Flowable 流程仓库服务，用于部署编译产物。 */
    @Autowired
    private RepositoryService repositoryService;

    /** 流程模型数据访问接口。 */
    @Autowired
    private ProcessModelMapper processModelMapper;

    /** 流程定义数据访问接口。 */
    @Autowired
    private ProcessDefinitionMapper processDefinitionMapper;

    /** 业务绑定数据访问接口，直接插入测试专用绑定，绕开管理页面。 */
    @Autowired
    private ProcessBindingMapper processBindingMapper;

    /** 节点审批人规则数据访问接口，运行时经此表解析任务候选人，必须随编译产物一并落库。 */
    @Autowired
    private NodeAssigneeRuleMapper nodeAssigneeRuleMapper;

    /** 流程实例数据访问接口，验证收尾后的真实终态。 */
    @Autowired
    private ProcessInstanceMapper processInstanceMapper;

    /** 用户任职记录数据访问接口，为测试申请人构造一条指向合成组织的主职任职记录。 */
    @Autowired
    private UserPositionMapper userPositionMapper;

    /** 业务活动申请锁数据访问接口，验证终态收尾后锁已释放。 */
    @Autowired
    private BusinessLockMapper businessLockMapper;

    /** 审批申请数据访问接口，读取 {@code processInstanceId} 等 VO 未暴露的字段。 */
    @Autowired
    private ApprovalRequestMapper approvalRequestMapper;

    /** 用户数据访问接口，验证自动通过分支真实创建了业务记录。 */
    @Autowired
    private UserMapper userMapper;

    /** 测试专用流程编码/用户编号自增序号，避免多个测试方法之间相互冲突。 */
    private static final AtomicInteger SEQ = new AtomicInteger();

    /** 测试专用合成组织 id 自增序号，取足够小的负数区间，避免与真实组织 id 冲突。 */
    private static final AtomicLong ORG_ID_SEQ = new AtomicLong(-920_000_000L);

    /** 测试专用申请人用户 id 自增序号，取足够大的正数区间，避免与真实用户 id 冲突。 */
    private static final AtomicLong APPLICANT_ID_SEQ = new AtomicLong(929_900_000L);

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    /**
     * 命中路径无审批节点、默认分支流转到 {@code outcome=APPROVED} 的结束节点：应在
     * {@code submit()} 返回前完成业务写入（真实创建 {@code tab_user} 记录）、回填
     * {@code resultTargetId}、审批人为空、使用固定的自动通过说明文案、释放业务活动锁；
     * {@link CurrentUserContext} 须正确恢复为申请人 id，不能残留为 {@code null}。
     */
    @Test
    void submit_shouldAutoApproveAndCreateBusinessRecord_whenNoApprovalNodeOnHitPath() {
        long applicantId = APPLICANT_ID_SEQ.getAndIncrement();
        long orgId = ORG_ID_SEQ.getAndDecrement();
        String processCode = "TEST_ZT_SUBMIT_APPROVED_" + SEQ.incrementAndGet();
        Long definitionId = deployZeroTaskDefinition(processCode, "APPROVED");
        bindOrgScope(FormFieldBizType.USER, ApprovalOperationType.CREATE, orgId, definitionId);
        givePrimaryPosition(applicantId, orgId);
        CurrentUserContext.setUserId(applicantId);

        UserCreateRequest payload = new UserCreateRequest();
        payload.setName("零任务自动通过测试用户");
        payload.setCode("ZT_AUTO_APPROVE_" + SEQ.incrementAndGet());

        WriteOperationResultVO<?> result = approvalRequestService.submit(
                FormFieldBizType.USER, ApprovalOperationType.CREATE, null, payload);

        ApprovalRequestVO vo = result.getApprovalRequest();
        assertThat(vo.getStatus()).isEqualTo(ApprovalRequestStatus.APPROVED);
        assertThat(vo.getApproverId()).isNull();
        assertThat(vo.getResultTargetId()).isNotNull();
        assertThat(vo.getOpinion()).contains("系统自动通过");
        assertThat(vo.getCurrentNodeName()).isNull();

        UserEntity created = userMapper.selectById(vo.getResultTargetId());
        assertThat(created).isNotNull();
        assertThat(created.getCode()).isEqualTo(payload.getCode());

        ApprovalRequestEntity persisted = approvalRequestMapper.selectById(vo.getId());
        assertThat(persisted.getStatus()).isEqualTo(ApprovalRequestStatus.APPROVED);
        ProcessInstanceEntity instance = processInstanceMapper.selectById(persisted.getProcessInstanceId());
        assertThat(instance.getStatus()).isEqualTo(ProcessInstanceStatus.APPROVED);

        BusinessLockEntity lock = businessLockMapper.selectOne(new LambdaQueryWrapper<BusinessLockEntity>()
                .eq(BusinessLockEntity::getBizType, FormFieldBizType.USER)
                .eq(BusinessLockEntity::getTargetKey, "REQUEST:" + vo.getId()));
        assertThat(lock).isNotNull();
        assertThat(lock.getActiveRequestId()).isNull();

        assertThat(CurrentUserContext.getUserId()).isEqualTo(applicantId);
    }

    /**
     * 命中路径无审批节点、默认分支流转到 {@code outcome=REJECTED} 的结束节点：不执行任何业务
     * 写入，申请状态为已拒绝，使用固定的自动拒绝说明文案，释放业务活动锁。
     */
    @Test
    void submit_shouldAutoRejectWithoutBusinessWrite_whenDefaultBranchLeadsToRejectedEnd() {
        long applicantId = APPLICANT_ID_SEQ.getAndIncrement();
        long orgId = ORG_ID_SEQ.getAndDecrement();
        String processCode = "TEST_ZT_SUBMIT_REJECTED_" + SEQ.incrementAndGet();
        Long definitionId = deployZeroTaskDefinition(processCode, "REJECTED");
        bindOrgScope(FormFieldBizType.USER, ApprovalOperationType.CREATE, orgId, definitionId);
        givePrimaryPosition(applicantId, orgId);
        CurrentUserContext.setUserId(applicantId);

        UserCreateRequest payload = new UserCreateRequest();
        payload.setName("零任务自动拒绝测试用户");
        payload.setCode("ZT_AUTO_REJECT_" + SEQ.incrementAndGet());

        WriteOperationResultVO<?> result = approvalRequestService.submit(
                FormFieldBizType.USER, ApprovalOperationType.CREATE, null, payload);

        ApprovalRequestVO vo = result.getApprovalRequest();
        assertThat(vo.getStatus()).isEqualTo(ApprovalRequestStatus.REJECTED);
        assertThat(vo.getApproverId()).isNull();
        assertThat(vo.getResultTargetId()).isNull();
        assertThat(vo.getOpinion()).contains("系统自动拒绝");

        assertThat(userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getCode, payload.getCode()))).isEmpty();

        ApprovalRequestEntity persisted = approvalRequestMapper.selectById(vo.getId());
        assertThat(persisted.getStatus()).isEqualTo(ApprovalRequestStatus.REJECTED);
        ProcessInstanceEntity instance = processInstanceMapper.selectById(persisted.getProcessInstanceId());
        assertThat(instance.getStatus()).isEqualTo(ProcessInstanceStatus.REJECTED);

        BusinessLockEntity lock = businessLockMapper.selectOne(new LambdaQueryWrapper<BusinessLockEntity>()
                .eq(BusinessLockEntity::getBizType, FormFieldBizType.USER)
                .eq(BusinessLockEntity::getTargetKey, "REQUEST:" + vo.getId()));
        assertThat(lock).isNotNull();
        assertThat(lock.getActiveRequestId()).isNull();
    }

    /**
     * 回归验证：命中路径经过至少一个审批节点、流程仍在运行（产生开放任务）的正常场景，
     * {@code submit()} 行为应与修复前完全一致——申请状态保持"待审批"，回填开放任务的
     * {@code flowableTaskId}，不触发任何自动终态收尾分支。
     */
    @Test
    void submit_shouldStayPendingAndRecordOpenTask_whenApprovalNodeExistsOnHitPath() {
        long applicantId = APPLICANT_ID_SEQ.getAndIncrement();
        long orgId = ORG_ID_SEQ.getAndDecrement();
        String processCode = "TEST_ZT_SUBMIT_RUNNING_" + SEQ.incrementAndGet();
        Long definitionId = deployApprovalDefinition(processCode, applicantId);
        bindOrgScope(FormFieldBizType.USER, ApprovalOperationType.CREATE, orgId, definitionId);
        givePrimaryPosition(applicantId, orgId);
        CurrentUserContext.setUserId(applicantId);

        UserCreateRequest payload = new UserCreateRequest();
        payload.setName("零任务修复回归测试用户");
        payload.setCode("ZT_REGRESSION_" + SEQ.incrementAndGet());

        WriteOperationResultVO<?> result = approvalRequestService.submit(
                FormFieldBizType.USER, ApprovalOperationType.CREATE, null, payload);

        ApprovalRequestVO vo = result.getApprovalRequest();
        assertThat(vo.getStatus()).isEqualTo(ApprovalRequestStatus.PENDING);
        assertThat(vo.getCurrentNodeName()).isNotBlank();
        assertThat(userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getCode, payload.getCode()))).isEmpty();

        ApprovalRequestEntity persisted = approvalRequestMapper.selectById(vo.getId());
        assertThat(persisted.getFlowableTaskId()).isNotBlank();
        ProcessInstanceEntity instance = processInstanceMapper.selectById(persisted.getProcessInstanceId());
        assertThat(instance.getStatus()).isEqualTo(ProcessInstanceStatus.RUNNING);
    }

    /**
     * 部署一个"开始 -> 条件节点（仅默认分支）-> 结束"的零任务流程，返回其
     * {@code tab_wf_process_definition.id}。
     *
     * @param processCode 流程编码
     * @param outcome     结束节点的终态方向：{@code APPROVED}/{@code REJECTED}
     * @return 流程定义 id
     */
    private Long deployZeroTaskDefinition(String processCode, String outcome) {
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("零任务提交测试流程")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        node(new ConditionNodeDslV2(), "cond"),
                        endNode("end", outcome)))
                .edges(List.of(
                        EdgeDslV2.builder().id("e1").source("start").target("cond").build(),
                        EdgeDslV2.builder().id("e2").source("cond").target("end").priority(1).build()))
                .build();
        return deployAndSeed(processCode, compiler.compile(dsl));
    }

    /**
     * 部署一个含单人审批节点（固定指派给申请人本人，允许自审）的正常流程，用于回归验证
     * "存在审批节点时行为不变"。
     *
     * @param processCode 流程编码
     * @param approverId  固定指派的审批人 id
     * @return 流程定义 id
     */
    private Long deployApprovalDefinition(String processCode, long approverId) {
        cn.nihility.rbac.workflow.dslv2.dto.ApprovalNodeDslV2 approval =
                new cn.nihility.rbac.workflow.dslv2.dto.ApprovalNodeDslV2();
        approval.setId("approve");
        approval.setType("APPROVAL");
        approval.setName("审批");
        cn.nihility.rbac.workflow.dslv2.dto.AssigneeConfigDsl assignee =
                new cn.nihility.rbac.workflow.dslv2.dto.AssigneeConfigDsl();
        assignee.setType(cn.nihility.rbac.workflow.dslv2.constant.AssigneeTypeV2.USER);
        assignee.setValue(String.valueOf(approverId));
        approval.setAssignee(assignee);
        approval.setEmptyPolicy(cn.nihility.rbac.workflow.dslv2.constant.EmptyPolicy.BLOCK);
        approval.setActions(new cn.nihility.rbac.workflow.dslv2.dto.ActionsConfigDsl());

        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("零任务提交回归测试流程")
                .nodes(List.of(node(new StartNodeDslV2(), "start"), approval, endNode("end", "APPROVED")))
                .edges(List.of(
                        EdgeDslV2.builder().id("e1").source("start").target("approve").build(),
                        EdgeDslV2.builder().id("e2").source("approve").target("end").build()))
                .build();
        return deployAndSeed(processCode, compiler.compile(dsl));
    }

    /**
     * 部署编译产物并落库最小可用的流程模型/流程定义/节点审批人规则种子数据。运行时按
     * {@code nodeId} 经 {@code tab_wf_node_assignee_rule} 解析任务候选人（不是靠 BPMN 自带的
     * {@code assignee} 表达式），零任务场景 {@code compiled.assigneeRules()} 为空、循环不插入
     * 任何行，与 {@link WorkflowModelCompilerV2IntegrationTest#deployAndSeed} 写法一致。
     */
    private Long deployAndSeed(String processCode, CompiledProcessV2 compiled) {
        Deployment deployment = repositoryService.createDeployment()
                .name("approval-zero-task-submit-integration-test")
                .addBpmnModel(processCode + ".bpmn20.xml", compiled.bpmnModel())
                .deploy();
        ProcessDefinition flowableDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("零任务提交集成测试流程-" + flowableDefinition.getKey())
                .modelJson("{}")
                .status(ProcessModelStatus.PUBLISHED)
                .enabled(true)
                .draftRevision(1L)
                .draftStatus("EDITING")
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        processModelMapper.insert(model);

        ProcessDefinitionEntity definition = ProcessDefinitionEntity.builder()
                .processModelId(model.getId())
                .processCode(processCode)
                .version(1)
                .schemaVersion(2)
                .flowableDefinitionKey(flowableDefinition.getKey())
                .flowableDefinitionId(flowableDefinition.getId())
                .modelJsonSnapshot("{}")
                .status(ProcessModelStatus.PUBLISHED)
                .publishedBy("test").publishedTime(now)
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        processDefinitionMapper.insert(definition);

        model.setCurrentDefinitionId(definition.getId());
        model.setUpdateTime(LocalDateTime.now());
        processModelMapper.updateById(model);

        for (NodeAssigneeRuleDraft draft : compiled.assigneeRules()) {
            nodeAssigneeRuleMapper.insert(NodeAssigneeRuleEntity.builder()
                    .processDefinitionId(definition.getId())
                    .nodeId(draft.nodeId())
                    .nodeName(draft.nodeName())
                    .nodeOrder(draft.nodeOrder())
                    .assigneeType(draft.assigneeType() == null ? null : draft.assigneeType().name())
                    .assigneeValue(draft.assigneeValue())
                    .approvalMode(draft.approvalMode() == null ? null : draft.approvalMode().name())
                    .approvalPercent(draft.approvalPercent())
                    .emptyAssigneeStrategy(draft.emptyAssigneeStrategy() == null ? null : draft.emptyAssigneeStrategy().name())
                    .fallbackRoleCode(draft.fallbackRoleCode())
                    .allowSelfApproval(draft.allowSelfApproval())
                    .allowTransfer(draft.allowTransfer())
                    .allowDelegate(draft.allowDelegate())
                    .allowAddSign(draft.allowAddSign())
                    .allowReturn(draft.allowReturn())
                    .rejectPolicy(draft.rejectPolicy())
                    .createBy("test").createTime(now).updateBy("test").updateTime(now)
                    .build());
        }
        return definition.getId();
    }

    /**
     * 插入一条 {@code scope_type=ORG} 的测试专用业务绑定，绕开既有 {@code GLOBAL} 兜底绑定。
     */
    private void bindOrgScope(String bizType, String operationType, long orgId, Long definitionId) {
        LocalDateTime now = LocalDateTime.now();
        processBindingMapper.insert(ProcessBindingEntity.builder()
                .bizType(bizType)
                .operationType(operationType)
                .scopeType("ORG")
                .scopeId(orgId)
                .definitionId(definitionId)
                .executionMode(ExecutionMode.LEGACY_SYNC)
                .revision(1L)
                .status(cn.nihility.rbac.workflow.constant.BindingStatus.ENABLED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());
    }

    /**
     * 为测试申请人插入一条指向合成组织的启用状态主职任职记录，令
     * {@code ApprovalRequestServiceImpl.resolveApplicantOrgId} 能解析出该合成组织 id，从而命中
     * 本类插入的 {@code scope_type=ORG} 绑定而非既有全局兜底绑定。
     */
    private void givePrimaryPosition(long userId, long orgId) {
        LocalDateTime now = LocalDateTime.now();
        userPositionMapper.insert(UserPositionEntity.builder()
                .userId(userId)
                .orgId(orgId)
                .positionType("primary")
                .showOrder(0)
                .status(cn.nihility.rbac.user.constant.PositionStatus.ENABLED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());
    }

    private ProcessNodeDslV2 node(ProcessNodeDslV2 node, String id) {
        node.setId(id);
        node.setType(node instanceof StartNodeDslV2 ? "START" : "CONDITION");
        return node;
    }

    private EndNodeDslV2 endNode(String id, String outcome) {
        EndNodeDslV2 end = new EndNodeDslV2();
        end.setId(id);
        end.setType("END");
        end.setOutcome(outcome);
        return end;
    }
}
