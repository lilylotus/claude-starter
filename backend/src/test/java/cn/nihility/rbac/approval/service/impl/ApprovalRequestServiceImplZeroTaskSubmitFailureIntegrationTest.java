package cn.nihility.rbac.approval.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.approval.constant.ApprovalOperationType;
import cn.nihility.rbac.approval.mapper.ApprovalRequestMapper;
import cn.nihility.rbac.approval.service.ApprovalRequestService;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.workflow.constant.BindingStatus;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.dslv2.compiler.CompiledProcessV2;
import cn.nihility.rbac.workflow.dslv2.compiler.WorkflowModelCompilerV2;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EndNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.StartNodeDslV2;
import cn.nihility.rbac.workflow.entity.ProcessBindingEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
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

/**
 * {@link ApprovalRequestServiceImpl#submit} 零任务自动通过分支业务写入失败时整体回滚的集成
 * 测试（fix-approval-zero-task-process-completion change tasks.md 2.5"业务写入失败"场景）。
 * <p>
 * 本类刻意不使用 {@link org.springframework.transaction.annotation.Transactional}
 * （与 {@code EngineBusinessSharedTransactionIntegrationTest}/
 * {@code ApprovalRequestServiceImplBusinessLockIntegrationTest} 同样的理由）：{@code submit()}
 * 自身的 {@code @Transactional(rollbackFor = Exception.class)} 只有在不被包进外层测试事务时才
 * 是真正的物理事务边界，抛出异常后才会立即对数据库执行真正的 ROLLBACK；如果测试方法本身也被
 * 包在一个外层测试事务里，断言时看到的只是同一事务内的"脏读"，无法证明数据已被真实回滚。
 * 测试自行创建的部署/模型/定义/绑定/任职/冲突用户等固定装置不受自动回滚保护，
 * {@link #cleanup()} 手动清理，避免在共享开发库残留数据。
 */
@SpringBootTest
class ApprovalRequestServiceImplZeroTaskSubmitFailureIntegrationTest {

    /** 审批申请业务接口。 */
    @Autowired
    private ApprovalRequestService approvalRequestService;

    /** DSL v2 编译器。 */
    @Autowired
    private WorkflowModelCompilerV2 compiler;

    /** Flowable 流程仓库服务。 */
    @Autowired
    private RepositoryService repositoryService;

    /** 流程模型数据访问接口。 */
    @Autowired
    private ProcessModelMapper processModelMapper;

    /** 流程定义数据访问接口。 */
    @Autowired
    private ProcessDefinitionMapper processDefinitionMapper;

    /** 业务绑定数据访问接口。 */
    @Autowired
    private ProcessBindingMapper processBindingMapper;

    /** 流程实例数据访问接口，用于统计回滚前后的记录数变化。 */
    @Autowired
    private ProcessInstanceMapper processInstanceMapper;

    /** 用户任职记录数据访问接口。 */
    @Autowired
    private UserPositionMapper userPositionMapper;

    /** 审批申请数据访问接口，用于统计回滚前后的记录数变化。 */
    @Autowired
    private ApprovalRequestMapper approvalRequestMapper;

    /** 用户数据访问接口，直接构造触发编码唯一性冲突所需的预置用户。 */
    @Autowired
    private UserMapper userMapper;

    /** 测试专用流程编码/用户编号自增序号。 */
    private static final AtomicInteger SEQ = new AtomicInteger();

    /** 测试专用合成组织 id 自增序号，取足够小的负数区间，避免与真实组织 id 冲突。 */
    private static final AtomicLong ORG_ID_SEQ = new AtomicLong(-930_000_000L);

    /** 测试专用申请人用户 id 自增序号，取足够大的正数区间，避免与真实用户 id 冲突。 */
    private static final AtomicLong APPLICANT_ID_SEQ = new AtomicLong(939_900_000L);

    /** 本方法动态部署的测试 BPMN 部署 id，测试结束后清理。 */
    private String deploymentId;

    /** 本方法落库的流程模型 id，测试结束后清理。 */
    private Long modelId;

    /** 本方法落库的流程定义 id，测试结束后清理。 */
    private Long definitionId;

    /** 本方法落库的业务绑定 id，测试结束后清理。 */
    private Long bindingId;

    /** 本方法为申请人插入的任职记录 id，测试结束后清理。 */
    private Long positionId;

    /** 本方法预置的、用于触发编码唯一性冲突的既有用户 id，测试结束后清理。 */
    private Long conflictingUserId;

    /**
     * 清理本方法新增的全部固定装置，避免在共享开发库中残留（本类没有测试事务自动回滚兜底）。
     */
    @AfterEach
    void cleanup() {
        if (deploymentId != null) {
            repositoryService.deleteDeployment(deploymentId, true);
        }
        if (positionId != null) {
            userPositionMapper.deleteById(positionId);
        }
        if (bindingId != null) {
            processBindingMapper.deleteById(bindingId);
        }
        if (definitionId != null) {
            processDefinitionMapper.deleteById(definitionId);
        }
        if (modelId != null) {
            processModelMapper.deleteById(modelId);
        }
        if (conflictingUserId != null) {
            userMapper.deleteById(conflictingUserId);
        }
        CurrentUserContext.clear();
    }

    /**
     * 零任务默认分支自动通过、但业务写入阶段因用户编码唯一性冲突而失败：{@code submit()} 整体
     * 应以异常失败，不留下任何半成品记录——申请记录、流程实例均不残留，也不产生第二条同编码的
     * 用户记录。
     */
    @Test
    void submit_shouldRollBackEverything_whenAutoApprovedBusinessWriteFails() {
        String duplicateCode = "ZT_CONFLICT_" + SEQ.incrementAndGet();
        conflictingUserId = createConflictingUser(duplicateCode);

        long applicantId = APPLICANT_ID_SEQ.getAndIncrement();
        long orgId = ORG_ID_SEQ.getAndDecrement();
        String processCode = "TEST_ZT_SUBMIT_FAIL_" + SEQ.incrementAndGet();
        definitionId = deployZeroTaskApprovedDefinition(processCode);
        bindingId = bindOrgScope(FormFieldBizType.USER, ApprovalOperationType.CREATE, orgId, definitionId);
        positionId = givePrimaryPosition(applicantId, orgId);
        CurrentUserContext.setUserId(applicantId);

        UserCreateRequest payload = new UserCreateRequest();
        payload.setName("零任务写入失败测试用户");
        payload.setCode(duplicateCode);

        long approvalRequestCountBefore = approvalRequestMapper.selectCount(null);
        long processInstanceCountBefore = processInstanceMapper.selectCount(null);

        assertThatThrownBy(() -> approvalRequestService.submit(
                FormFieldBizType.USER, ApprovalOperationType.CREATE, null, payload))
                .isInstanceOf(BusinessException.class);

        assertThat(approvalRequestMapper.selectCount(null)).isEqualTo(approvalRequestCountBefore);
        assertThat(processInstanceMapper.selectCount(null)).isEqualTo(processInstanceCountBefore);
        assertThat(userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getCode, duplicateCode))).hasSize(1);
    }

    /**
     * 创建一条用于触发编码唯一性冲突的既有用户记录，绕开 {@code UserService} 依赖的密码/日志/
     * 同步事件等无关基础设施，直接构造满足非空约束的最小合法行。
     */
    private Long createConflictingUser(String code) {
        LocalDateTime now = LocalDateTime.now();
        UserEntity entity = UserEntity.builder()
                .name("既有冲突用户")
                .code(code)
                .showOrder(0)
                .status(UserStatus.ENABLED)
                .version(1L)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        userMapper.insert(entity);
        return entity.getId();
    }

    /**
     * 部署一个"开始 -> 条件节点（仅默认分支）-> 结束（outcome=APPROVED）"的零任务流程。
     */
    private Long deployZeroTaskApprovedDefinition(String processCode) {
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("零任务提交失败回滚测试流程")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        node(new ConditionNodeDslV2(), "cond"),
                        endNode("end", "APPROVED")))
                .edges(List.of(
                        EdgeDslV2.builder().id("e1").source("start").target("cond").build(),
                        EdgeDslV2.builder().id("e2").source("cond").target("end").priority(1).build()))
                .build();

        CompiledProcessV2 compiled = compiler.compile(dsl);
        Deployment deployment = repositoryService.createDeployment()
                .name("approval-zero-task-submit-failure-integration-test")
                .addBpmnModel(processCode + ".bpmn20.xml", compiled.bpmnModel())
                .deploy();
        deploymentId = deployment.getId();
        ProcessDefinition flowableDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploymentId)
                .singleResult();

        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("零任务提交失败回滚测试流程-" + flowableDefinition.getKey())
                .modelJson("{}")
                .status(ProcessModelStatus.PUBLISHED)
                .enabled(true)
                .draftRevision(1L)
                .draftStatus("EDITING")
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        processModelMapper.insert(model);
        modelId = model.getId();

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
        return definition.getId();
    }

    /** 插入一条 {@code scope_type=ORG} 的测试专用业务绑定，绕开既有 {@code GLOBAL} 兜底绑定。 */
    private Long bindOrgScope(String bizType, String operationType, long orgId, Long definitionId) {
        LocalDateTime now = LocalDateTime.now();
        ProcessBindingEntity binding = ProcessBindingEntity.builder()
                .bizType(bizType)
                .operationType(operationType)
                .scopeType("ORG")
                .scopeId(orgId)
                .definitionId(definitionId)
                .executionMode(ExecutionMode.LEGACY_SYNC)
                .revision(1L)
                .status(BindingStatus.ENABLED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        processBindingMapper.insert(binding);
        return binding.getId();
    }

    /**
     * 为测试申请人插入一条指向合成组织的启用状态主职任职记录，令绑定解析命中本类插入的
     * {@code scope_type=ORG} 绑定而非既有全局兜底绑定。
     */
    private Long givePrimaryPosition(long userId, long orgId) {
        LocalDateTime now = LocalDateTime.now();
        UserPositionEntity position = UserPositionEntity.builder()
                .userId(userId)
                .orgId(orgId)
                .positionType("primary")
                .showOrder(0)
                .status(cn.nihility.rbac.user.constant.PositionStatus.ENABLED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        userPositionMapper.insert(position);
        return position.getId();
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
