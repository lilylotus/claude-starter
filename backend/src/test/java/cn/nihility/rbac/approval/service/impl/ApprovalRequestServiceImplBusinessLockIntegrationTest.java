package cn.nihility.rbac.approval.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.approval.constant.ApprovalOperationType;
import cn.nihility.rbac.approval.dto.WriteOperationResultVO;
import cn.nihility.rbac.approval.service.ApprovalRequestService;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实数据库集成测试：验证 {@code tab_wf_business_lock} 业务活动申请锁经
 * {@link ApprovalRequestServiceImpl#submit} 接入后，同一业务目标同时只允许一条运行中的审批
 * 申请（production-approval-lifecycle change design.md 第8节，tasks.md 6.2）。
 * <p>
 * 本类刻意不使用 {@link org.springframework.transaction.annotation.Transactional} 测试注解
 * （与 {@code EngineBusinessSharedTransactionIntegrationTest} 同样的理由）：本轮验证的正是
 * "锁行占用/释放确实跨多次独立的物理事务生效"，如果整个测试方法被包在一个外层测试事务里，
 * 第二次 {@code submit} 与第一次共享同一个未提交事务，无法证明锁是"真实持久化状态"而非
 * "同一事务内可见的临时状态"。每个测试方法结束时手动 {@code cancel} 清理，不依赖自动回滚。
 * 使用 {@code USER} 业务类型 + {@code DISABLE} 操作类型：{@code ApprovalRequestServiceImpl
 * .validateScope} 对 {@code USER} 类型直接放行，不要求 {@code targetId} 对应真实存在的用户
 * 记录，测试无需额外构造业务数据即可覆盖锁本身的行为。
 */
@SpringBootTest
class ApprovalRequestServiceImplBusinessLockIntegrationTest {

    /** 审批申请业务接口。 */
    @Autowired
    private ApprovalRequestService approvalRequestService;

    /** 测试用目标 id 序号，取足够大的负数区间，避免与真实用户 id 冲突。 */
    private static final AtomicLong TARGET_ID_SEQ = new AtomicLong(-900_000_000L);

    /** 本方法内提交但尚未清理的申请 id，测试结束后统一撤回，释放业务活动锁。 */
    private Long pendingRequestIdToCleanup;

    @BeforeEach
    void setUp() {
        CurrentUserContext.setUserId(1L);
        pendingRequestIdToCleanup = null;
    }

    @AfterEach
    void tearDown() {
        if (pendingRequestIdToCleanup != null) {
            try {
                approvalRequestService.cancel(pendingRequestIdToCleanup);
            } catch (BusinessException ignored) {
                // 测试方法内部已经撤回过，忽略重复清理。
            }
        }
        CurrentUserContext.clear();
    }

    /**
     * 同一业务目标连续两次发起：第一次成功进入待审批，第二次应被业务活动锁拒绝，明确提示
     * "该目标已有进行中的审批"，不产生第二条流程实例。
     */
    @Test
    void submit_shouldRejectSecondRequest_whenSameTargetHasRunningApproval() {
        long targetId = TARGET_ID_SEQ.getAndDecrement();

        WriteOperationResultVO<?> first = approvalRequestService.submit(
                FormFieldBizType.USER, ApprovalOperationType.DISABLE, targetId, null);
        pendingRequestIdToCleanup = first.getApprovalRequest().getId();

        assertThatThrownBy(() -> approvalRequestService.submit(
                FormFieldBizType.USER, ApprovalOperationType.DISABLE, targetId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该目标已有进行中的审批");
    }

    /**
     * 首次申请撤回（到达终态 CANCELLED）后释放业务活动锁，同一业务目标应能再次成功发起新的
     * 申请，不再被锁占用。
     */
    @Test
    void submit_shouldSucceed_afterPreviousRequestReachedTerminalState() {
        long targetId = TARGET_ID_SEQ.getAndDecrement();

        WriteOperationResultVO<?> first = approvalRequestService.submit(
                FormFieldBizType.USER, ApprovalOperationType.DISABLE, targetId, null);
        Long firstRequestId = first.getApprovalRequest().getId();
        approvalRequestService.cancel(firstRequestId);

        WriteOperationResultVO<?> second = approvalRequestService.submit(
                FormFieldBizType.USER, ApprovalOperationType.DISABLE, targetId, null);
        pendingRequestIdToCleanup = second.getApprovalRequest().getId();

        assertThat(second.getApprovalRequest().getId()).isNotEqualTo(firstRequestId);
    }

    /**
     * 不同业务目标之间互不影响：即便第一个目标的申请仍处于运行中，另一个不同的
     * {@code targetId} 也应能正常发起。
     */
    @Test
    void submit_shouldSucceed_forDifferentTarget() {
        long targetIdA = TARGET_ID_SEQ.getAndDecrement();
        long targetIdB = TARGET_ID_SEQ.getAndDecrement();

        WriteOperationResultVO<?> requestA = approvalRequestService.submit(
                FormFieldBizType.USER, ApprovalOperationType.DISABLE, targetIdA, null);
        WriteOperationResultVO<?> requestB = approvalRequestService.submit(
                FormFieldBizType.USER, ApprovalOperationType.DISABLE, targetIdB, null);

        assertThat(requestA.getApprovalRequest().getId()).isNotEqualTo(requestB.getApprovalRequest().getId());

        approvalRequestService.cancel(requestA.getApprovalRequest().getId());
        approvalRequestService.cancel(requestB.getApprovalRequest().getId());
    }
}
