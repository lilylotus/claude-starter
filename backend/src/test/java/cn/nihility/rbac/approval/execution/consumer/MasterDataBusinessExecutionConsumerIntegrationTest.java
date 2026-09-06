package cn.nihility.rbac.approval.execution.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.approval.constant.ApprovalOperationType;
import cn.nihility.rbac.approval.constant.ApprovalRequestStatus;
import cn.nihility.rbac.approval.entity.ApprovalRequestEntity;
import cn.nihility.rbac.approval.execution.dto.BusinessExecutionTriggerPayload;
import cn.nihility.rbac.approval.execution.entity.BusinessExecutionEntity;
import cn.nihility.rbac.approval.execution.mapper.BusinessExecutionMapper;
import cn.nihility.rbac.approval.mapper.ApprovalRequestMapper;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.dto.OrgVO;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.org.service.OrgService;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.dto.UserPositionRequest;
import cn.nihility.rbac.user.dto.UserUpdateRequest;
import cn.nihility.rbac.user.dto.UserVO;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.service.UserService;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ExecutionStatus;
import cn.nihility.rbac.workflow.outbox.constant.OutboxEventStatus;
import cn.nihility.rbac.workflow.outbox.constant.OutboxEventType;
import cn.nihility.rbac.workflow.outbox.entity.EventConsumeEntity;
import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;
import cn.nihility.rbac.workflow.outbox.mapper.EventConsumeMapper;
import cn.nihility.rbac.workflow.outbox.mapper.OutboxEventMapper;
import cn.nihility.rbac.workflow.outbox.service.OutboxEventService;
import cn.nihility.rbac.workflow.outbox.support.OutboxEventConsumptionCoordinator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link MasterDataBusinessExecutionConsumer} 真实数据库端到端集成测试
 * （production-approval-lifecycle change design.md 第9/10/203行，tasks.md 7.3）。不使用测试
 * 专用回滚事务、不 mock DB 层，理由与 {@code OutboxEventConsumptionCoordinatorIntegrationTest}
 * 一致；本消费者是真实 {@code @Component}，直接 {@code @Autowired} 真实的
 * {@link OutboxEventConsumptionCoordinator} bean 即可让它自动纳入编排（与 7.2 手动 {@code new}
 * 编排器、显式传入测试桩消费者的做法不同——本类不是桩，是生产实现）。
 * <p>
 * 由于目前没有任何生产代码会在 {@code execution_mode=RELIABLE_ASYNC} 时真正发布触发事件
 * （7.5 范围），本类直接构造 {@code tab_approval_request} 行（模拟"已终审通过、等待异步执行"
 * 的状态）并手动 {@code publish} 一条 {@link OutboxEventType#PROCESS_APPROVED} 触发事件，
 * 而不经过 {@code ApprovalRequestServiceImpl.submit/approve} 的真实入口。
 */
@SpringBootTest
class MasterDataBusinessExecutionConsumerIntegrationTest {

    /** 测试用 id 序号，取足够大的负数区间，避免与真实数据冲突。 */
    private static final AtomicLong ID_SEQ = new AtomicLong(-910_000_000L);

    /** 审批申请数据访问接口。 */
    @Autowired
    private ApprovalRequestMapper approvalRequestMapper;

    /** 业务执行尝试记录数据访问接口。 */
    @Autowired
    private BusinessExecutionMapper businessExecutionMapper;

    /** Outbox 事件生产/领取服务。 */
    @Autowired
    private OutboxEventService outboxEventService;

    /** Outbox 事件数据访问接口。 */
    @Autowired
    private OutboxEventMapper outboxEventMapper;

    /** Outbox 事件消费去重记录数据访问接口。 */
    @Autowired
    private EventConsumeMapper eventConsumeMapper;

    /** Outbox 事件消费编排组件，容器已自动收集本轮新增的真实消费者。 */
    @Autowired
    private OutboxEventConsumptionCoordinator coordinator;

    /** 组织业务接口。 */
    @Autowired
    private OrgService orgService;

    /** 用户业务接口。 */
    @Autowired
    private UserService userService;

    /** 组织数据访问接口，测试结束后硬删除测试夹具数据。 */
    @Autowired
    private OrgMapper orgMapper;

    /** 用户数据访问接口，测试结束后硬删除测试夹具数据。 */
    @Autowired
    private UserMapper userMapper;

    /** 用户任职数据访问接口，测试结束后硬删除测试夹具数据。 */
    @Autowired
    private UserPositionMapper userPositionMapper;

    /** 本方法内创建的审批申请 id，测试结束后据此清理关联的三张表行。 */
    private Long requestIdToCleanup;

    /** 本方法内发布的 Outbox 事件 id，测试结束后据此清理。 */
    private String eventIdToCleanup;

    /** 本方法内创建的组织 id 列表，测试结束后硬删除。 */
    private final List<Long> orgIdsToCleanup = new ArrayList<>();

    /** 本方法内创建的用户 id 列表，测试结束后硬删除（级联硬删除其任职记录）。 */
    private final List<Long> userIdsToCleanup = new ArrayList<>();

    @BeforeEach
    void setUp() {
        CurrentUserContext.setUserId(nextId());
        requestIdToCleanup = null;
        eventIdToCleanup = null;
    }

    @AfterEach
    void tearDown() {
        if (requestIdToCleanup != null) {
            businessExecutionMapper.delete(new LambdaQueryWrapper<BusinessExecutionEntity>()
                    .eq(BusinessExecutionEntity::getRequestId, requestIdToCleanup));
            approvalRequestMapper.deleteById(requestIdToCleanup);
        }
        if (eventIdToCleanup != null) {
            eventConsumeMapper.delete(
                    new LambdaQueryWrapper<EventConsumeEntity>().eq(EventConsumeEntity::getEventId, eventIdToCleanup));
            outboxEventMapper.delete(
                    new LambdaQueryWrapper<OutboxEventEntity>().eq(OutboxEventEntity::getEventId, eventIdToCleanup));
        }
        userIdsToCleanup.forEach(userId -> {
            userPositionMapper.delete(new LambdaQueryWrapper<UserPositionEntity>()
                    .eq(UserPositionEntity::getUserId, userId));
            userMapper.deleteById(userId);
        });
        orgIdsToCleanup.forEach(orgMapper::deleteById);
        CurrentUserContext.clear();
    }

    /**
     * ORG {@code CREATE} 申请通过异步执行适配器路径真实执行：{@code tab_wf_business_execution}
     * 落库一条 {@code SUCCEEDED} 尝试记录，{@code tab_approval_request.execution_status}
     * 原子转 {@code SUCCEEDED} 且 {@code result_target_id} 回填为新组织 id，新组织的字段与
     * 直接调用 {@link OrgService#create} 得到的结果一致。
     */
    @Test
    void consume_shouldCreateOrgAsynchronously_andRecordSuccessWithResultTargetId() {
        String code = "IT-ORG-CREATE-" + UUID.randomUUID();
        OrgCreateRequest request = new OrgCreateRequest();
        request.setName("异步创建测试组织");
        request.setCode(code);
        request.setParentId(0L);
        Long submitterId = nextId();
        ApprovalRequestEntity entity = insertApprovalRequest(
                FormFieldBizType.ORG, ApprovalOperationType.CREATE, null, request, submitterId);

        OutboxEventEntity claimed = publishAndClaim(entity.getId());
        coordinator.consume(claimed);

        ApprovalRequestEntity reloaded = approvalRequestMapper.selectById(entity.getId());
        assertThat(reloaded.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(reloaded.getResultTargetId()).isNotNull();
        orgIdsToCleanup.add(reloaded.getResultTargetId());

        List<BusinessExecutionEntity> attempts = businessExecutionMapper.selectList(
                new LambdaQueryWrapper<BusinessExecutionEntity>()
                        .eq(BusinessExecutionEntity::getRequestId, entity.getId()));
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getAttemptNo()).isEqualTo(1);
        assertThat(attempts.get(0).getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(attempts.get(0).getResultTargetId()).isEqualTo(reloaded.getResultTargetId());

        OrgVO created = orgService.getById(reloaded.getResultTargetId());
        assertThat(created.getName()).isEqualTo("异步创建测试组织");
        assertThat(created.getCode()).isEqualTo(code);
        assertThat(created.getParentId()).isZero();
    }

    /**
     * ORG {@code UPDATE} 申请通过异步执行适配器路径真实执行：既有组织的名称被真实更新，
     * {@code result_target_id} 因非 {@code CREATE} 场景保持不变（{@code null}）。
     */
    @Test
    void consume_shouldUpdateOrgAsynchronously_andRecordSuccess() {
        Long submitterId = nextId();
        CurrentUserContext.setUserId(submitterId);
        OrgCreateRequest createRequest = new OrgCreateRequest();
        createRequest.setName("异步更新测试组织-旧");
        createRequest.setCode("IT-ORG-UPDATE-" + UUID.randomUUID());
        createRequest.setParentId(0L);
        OrgVO existing = orgService.create(createRequest);
        orgIdsToCleanup.add(existing.getId());

        OrgUpdateRequest updateRequest = new OrgUpdateRequest();
        updateRequest.setName("异步更新测试组织-新");
        updateRequest.setCode(createRequest.getCode());
        updateRequest.setParentId(0L);
        ApprovalRequestEntity entity = insertApprovalRequest(
                FormFieldBizType.ORG, ApprovalOperationType.UPDATE, existing.getId(), updateRequest, submitterId);

        OutboxEventEntity claimed = publishAndClaim(entity.getId());
        coordinator.consume(claimed);

        ApprovalRequestEntity reloaded = approvalRequestMapper.selectById(entity.getId());
        assertThat(reloaded.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(reloaded.getResultTargetId()).isNull();

        List<BusinessExecutionEntity> attempts = businessExecutionMapper.selectList(
                new LambdaQueryWrapper<BusinessExecutionEntity>()
                        .eq(BusinessExecutionEntity::getRequestId, entity.getId()));
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);

        assertThat(orgService.getById(existing.getId()).getName()).isEqualTo("异步更新测试组织-新");
    }

    /**
     * USER {@code UPDATE} 申请的任职记录应按"整体重建"语义通过异步执行适配器真实生效：既有
     * 任职记录（携带 {@code id}）被更新到新组织，新增任职记录（不携带 {@code id}）被真实创建，
     * 更新后用户任职集合与本次请求携带的期望集合完全一致（{@code UserServiceImpl#update}
     * 既有实现语义，本轮只是换一条调用路径，见 {@code UserServiceImpl#syncPositions}）。
     */
    @Test
    void consume_shouldRebuildUserPositionsCompletely_onUpdate() {
        Long submitterId = nextId();
        CurrentUserContext.setUserId(submitterId);
        OrgCreateRequest orgARequest = new OrgCreateRequest();
        orgARequest.setName("任职整体更新-组织A");
        orgARequest.setCode("IT-USER-POS-A-" + UUID.randomUUID());
        orgARequest.setParentId(0L);
        OrgVO orgA = orgService.create(orgARequest);
        orgIdsToCleanup.add(orgA.getId());

        OrgCreateRequest orgBRequest = new OrgCreateRequest();
        orgBRequest.setName("任职整体更新-组织B");
        orgBRequest.setCode("IT-USER-POS-B-" + UUID.randomUUID());
        orgBRequest.setParentId(0L);
        OrgVO orgB = orgService.create(orgBRequest);
        orgIdsToCleanup.add(orgB.getId());

        UserPositionRequest initialPosition = new UserPositionRequest();
        initialPosition.setOrgId(orgA.getId());
        initialPosition.setPositionType("primary");
        UserCreateRequest userCreateRequest = new UserCreateRequest();
        userCreateRequest.setName("任职整体更新测试用户");
        userCreateRequest.setCode("IT-USER-POS-" + UUID.randomUUID());
        userCreateRequest.setPositions(List.of(initialPosition));
        UserVO createdUser = userService.create(userCreateRequest);
        userIdsToCleanup.add(createdUser.getId());
        assertThat(createdUser.getPositions()).hasSize(1);
        Long existingPositionId = createdUser.getPositions().get(0).getId();

        // 本次请求携带的期望任职集合：既有任职记录挪到组织 B（携带 id 触发更新），
        // 并新增一条组织 A 的兼职记录（不携带 id 触发新增）——整体重建语义下服务端应以
        // 本次列表为最终状态，不是简单的增量新增。
        UserPositionRequest keptPosition = new UserPositionRequest();
        keptPosition.setId(existingPositionId);
        keptPosition.setOrgId(orgB.getId());
        keptPosition.setPositionType("primary");
        UserPositionRequest newPosition = new UserPositionRequest();
        newPosition.setOrgId(orgA.getId());
        newPosition.setPositionType("part_time");
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setName(userCreateRequest.getName());
        updateRequest.setCode(userCreateRequest.getCode());
        updateRequest.setPositions(List.of(keptPosition, newPosition));

        ApprovalRequestEntity entity = insertApprovalRequest(
                FormFieldBizType.USER, ApprovalOperationType.UPDATE, createdUser.getId(), updateRequest, submitterId);

        OutboxEventEntity claimed = publishAndClaim(entity.getId());
        coordinator.consume(claimed);

        ApprovalRequestEntity reloaded = approvalRequestMapper.selectById(entity.getId());
        assertThat(reloaded.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);

        UserVO reloadedUser = userService.getById(createdUser.getId());
        assertThat(reloadedUser.getPositions()).hasSize(2);
        assertThat(reloadedUser.getPositions().stream()
                .filter(position -> position.getId().equals(existingPositionId))
                .findFirst()
                .orElseThrow()
                .getOrgId())
                .as("既有任职记录应被整体更新到新组织，而不是保留旧组织")
                .isEqualTo(orgB.getId());
        assertThat(reloadedUser.getPositions().stream()
                .anyMatch(position -> !position.getId().equals(existingPositionId)
                        && position.getOrgId().equals(orgA.getId())
                        && "part_time".equals(position.getPositionType())))
                .as("请求中不携带 id 的新任职记录应被真实新增")
                .isTrue();
    }

    /**
     * 幂等/防重复执行：同一事件被重复"消费"（模拟崩溃后重放，直接第二次调用编排逻辑）时，
     * 业务写操作不会被真正执行第二次——这一层由 7.2 的消费唯一键去重保证，本测试只补一个
     * 端到端证据，证明业务执行适配器接入这套机制后确实享受到了这个保证。
     */
    @Test
    void consume_shouldNotReexecuteBusinessWrite_onDuplicateReplay() {
        String code = "IT-ORG-DEDUP-" + UUID.randomUUID();
        OrgCreateRequest request = new OrgCreateRequest();
        request.setName("幂等测试组织");
        request.setCode(code);
        request.setParentId(0L);
        Long submitterId = nextId();
        ApprovalRequestEntity entity = insertApprovalRequest(
                FormFieldBizType.ORG, ApprovalOperationType.CREATE, null, request, submitterId);

        OutboxEventEntity claimed = publishAndClaim(entity.getId());
        coordinator.consume(claimed);
        ApprovalRequestEntity afterFirst = approvalRequestMapper.selectById(entity.getId());
        assertThat(afterFirst.getResultTargetId()).isNotNull();
        orgIdsToCleanup.add(afterFirst.getResultTargetId());

        coordinator.consume(claimed);

        List<BusinessExecutionEntity> attempts = businessExecutionMapper.selectList(
                new LambdaQueryWrapper<BusinessExecutionEntity>()
                        .eq(BusinessExecutionEntity::getRequestId, entity.getId()));
        assertThat(attempts).as("重放不应产生第二条执行尝试记录").hasSize(1);
        ApprovalRequestEntity afterSecond = approvalRequestMapper.selectById(entity.getId());
        assertThat(afterSecond.getResultTargetId()).isEqualTo(afterFirst.getResultTargetId());
        List<OrgEntity> orgsWithCode = orgMapper.selectList(
                new LambdaQueryWrapper<OrgEntity>()
                        .eq(OrgEntity::getCode, code));
        assertThat(orgsWithCode).as("重放不应真正创建第二条同编码组织").hasSize(1);
    }

    /**
     * 失败场景：目标组织在触发事件到达前已被删除，执行时 {@link OrgService#getById}/
     * {@code update} 均会抛出 {@link BusinessException}
     * （"组织不存在"），消费者应将其映射为 {@code FAILED_MANUAL} 而不是让异常穿透触发
     * Outbox 层的自动重试——目标已被删除是非暂时性问题，重试无法自愈，需人工/重新审批处理。
     */
    @Test
    void consume_shouldMapDeletedTargetFailure_toFailedManual() {
        Long submitterId = nextId();
        CurrentUserContext.setUserId(submitterId);
        OrgCreateRequest createRequest = new OrgCreateRequest();
        createRequest.setName("失败场景测试组织");
        createRequest.setCode("IT-ORG-FAIL-" + UUID.randomUUID());
        createRequest.setParentId(0L);
        OrgVO existing = orgService.create(createRequest);
        orgIdsToCleanup.add(existing.getId());
        orgService.delete(existing.getId());

        OrgUpdateRequest updateRequest = new OrgUpdateRequest();
        updateRequest.setName("不应生效的新名称");
        updateRequest.setCode(createRequest.getCode());
        updateRequest.setParentId(0L);
        ApprovalRequestEntity entity = insertApprovalRequest(
                FormFieldBizType.ORG, ApprovalOperationType.UPDATE, existing.getId(), updateRequest, submitterId);

        OutboxEventEntity claimed = publishAndClaim(entity.getId());
        coordinator.consume(claimed);

        ApprovalRequestEntity reloaded = approvalRequestMapper.selectById(entity.getId());
        assertThat(reloaded.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED_MANUAL);

        List<BusinessExecutionEntity> attempts = businessExecutionMapper.selectList(
                new LambdaQueryWrapper<BusinessExecutionEntity>()
                        .eq(BusinessExecutionEntity::getRequestId, entity.getId()));
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED_MANUAL);
        assertThat(attempts.get(0).getErrorCode()).isNotBlank();

        OutboxEventEntity finalEvent = outboxEventMapper.selectById(claimed.getId());
        assertThat(finalEvent.getStatus()).as("手动失败已妥善落库，不应触发自动重试")
                .isEqualTo(OutboxEventStatus.SUCCEEDED);
    }

    /** 生成测试用递减 id。 */
    private Long nextId() {
        return ID_SEQ.getAndDecrement();
    }

    /** 构造并落库一条模拟"已终审通过、等待异步执行"的审批申请。 */
    private ApprovalRequestEntity insertApprovalRequest(
            String bizType,
            String operationType,
            Long targetId,
            Object payload,
            Long submitterId) {
        LocalDateTime now = LocalDateTime.now();
        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .bizType(bizType)
                .operationType(operationType)
                .targetId(targetId)
                .requestPayload(JacksonUtils.toJson(payload))
                .status(ApprovalRequestStatus.APPROVED)
                .executionMode(ExecutionMode.RELIABLE_ASYNC)
                .executionStatus(ExecutionStatus.PENDING)
                .createBy(submitterId.toString())
                .createTime(now)
                .updateBy(submitterId.toString())
                .updateTime(now)
                .build();
        approvalRequestMapper.insert(entity);
        requestIdToCleanup = entity.getId();
        return entity;
    }

    /** 发布一条 {@code PROCESS_APPROVED} 触发事件并立即领取。 */
    private OutboxEventEntity publishAndClaim(Long requestId) {
        String eventId = "IT-BUSINESS-EXECUTOR-" + UUID.randomUUID();
        eventIdToCleanup = eventId;
        outboxEventService.publish(
                eventId,
                "approval-request-" + requestId,
                1L,
                OutboxEventType.PROCESS_APPROVED,
                new BusinessExecutionTriggerPayload(requestId));
        return outboxEventService.claimDueEvents(50).stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .findFirst()
                .orElseThrow();
    }
}
