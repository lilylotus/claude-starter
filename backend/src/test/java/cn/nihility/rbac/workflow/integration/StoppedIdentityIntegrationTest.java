package cn.nihility.rbac.workflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 任务处理前"停用身份检测"对着真实引擎的集成测试（production-approval-lifecycle change
 * tasks.md 5.4）：操作人对应的 {@code tab_user} 行真实存在且状态为停用时，
 * {@code FlowableWorkflowService#approve} 必须明确拒绝，不得静默放行。
 */
class StoppedIdentityIntegrationTest extends AbstractWorkflowEngineIntegrationTest {

    /** 单人固定审批人测试流程资源路径，复用 levelOne 节点即可。 */
    private static final String TRANSFER_DELEGATE_RETURN_BPMN = "processes/test-transfer-delegate-return.bpmn20.xml";

    /** 用户数据访问接口，用于真实插入一条停用状态的用户行。 */
    @Autowired
    private UserMapper userMapper;

    /**
     * 操作人对应真实存在的 {@code tab_user} 行、状态为停用时，处理任务应被拒绝。
     */
    @Test
    void approve_shouldReject_whenOperatorUserIsDisabled() {
        Long disabledUserId = insertUser("停用测试用户", UserStatus.DISABLED);

        ProcessFixture fixture = deployAndSeed(TRANSFER_DELEGATE_RETURN_BPMN, List.of(
                NodeSeed.simple("levelOne", "第一级审批", ApprovalMode.SINGLE, null, disabledUserId.toString())));
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 990001L, null, null, null,
                fixture.processDefinitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> tasks = approvalTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApprovalTaskEntity>()
                        .eq(ApprovalTaskEntity::getProcessInstanceId, started.processInstanceId())
                        .eq(ApprovalTaskEntity::getNodeId, "levelOne"));
        assertThat(tasks).hasSize(1);
        Long taskId = tasks.get(0).getId();

        assertThatThrownBy(() -> workflowService.approve(new ApproveCommand(taskId, disabledUserId, "同意", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停用");

        ApprovalTaskEntity stillPending = approvalTaskMapper.selectById(taskId);
        assertThat(stillPending.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    /**
     * 操作人对应的 {@code tab_user} 行查不到（如测试夹具使用的合成 id）时不拒绝，保持既有
     * 行为不变，不误伤用户体系之外的历史/测试调用方。
     */
    @Test
    void approve_shouldPass_whenOperatorUserRowNotFound() {
        ProcessFixture fixture = deployAndSeed(TRANSFER_DELEGATE_RETURN_BPMN, List.of(
                NodeSeed.simple("levelOne", "第一级审批", ApprovalMode.SINGLE, null, "710099")));
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 990002L, null, null, null,
                fixture.processDefinitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> tasks = approvalTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApprovalTaskEntity>()
                        .eq(ApprovalTaskEntity::getProcessInstanceId, started.processInstanceId())
                        .eq(ApprovalTaskEntity::getNodeId, "levelOne"));
        Long taskId = tasks.get(0).getId();

        workflowService.approve(new ApproveCommand(taskId, 710099L, "同意", null));

        ApprovalTaskEntity completed = approvalTaskMapper.selectById(taskId);
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    /**
     * 插入一条真实的 {@code tab_user} 行，返回其自增主键 id。
     */
    private Long insertUser(String name, int status) {
        LocalDateTime now = LocalDateTime.now();
        UserEntity user = UserEntity.builder()
                .name(name)
                .code("TEST_STOPPED_IDENTITY_" + System.nanoTime())
                .gender("unknown")
                .showOrder(0)
                .status(status)
                .version(1L)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        userMapper.insert(user);
        return user.getId();
    }
}
