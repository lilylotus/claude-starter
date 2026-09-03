package cn.nihility.rbac.approval.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.dto.AppUpdateRequest;
import cn.nihility.rbac.app.dto.AppVO;
import cn.nihility.rbac.app.service.AppService;
import cn.nihility.rbac.approval.constant.ApprovalOperationType;
import cn.nihility.rbac.approval.constant.ApprovalRequestStatus;
import cn.nihility.rbac.approval.dto.ApprovalRequestVO;
import cn.nihility.rbac.approval.dto.WriteOperationResultVO;
import cn.nihility.rbac.approval.entity.ApprovalRequestEntity;
import cn.nihility.rbac.approval.mapper.ApprovalRequestMapper;
import cn.nihility.rbac.approval.service.ApprovalProcessService;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.dto.OrgVO;
import cn.nihility.rbac.org.service.OrgService;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.dto.UserPositionRequest;
import cn.nihility.rbac.user.dto.UserUpdateRequest;
import cn.nihility.rbac.user.dto.UserVO;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.service.PositionService;
import cn.nihility.rbac.user.service.UserDisplayService;
import cn.nihility.rbac.user.service.UserService;
import cn.nihility.rbac.workflow.assignee.support.TaskAuthorizationService;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ApprovalRequestServiceImpl} 核心状态流转单元测试，覆盖两级审批（部门负责人 ->
 * 安全管理员）改造后的语义：非最终节点通过仅推进流程、最终节点通过才执行业务写操作、
 * 任一级拒绝直接终止、已过第一级不能撤回、待我审批按当前节点候选人过滤
 * （master-data-approval-workflow spec.md）。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalRequestServiceImplTest {

    /** 待审批申请关联的流程实例 id。 */
    private static final Long PROCESS_INSTANCE_ID = 100L;

    /** 待处理审批任务 id。 */
    private static final Long TASK_ID = 555L;

    @Mock
    private ApprovalRequestMapper mapper;

    @Mock
    private ApprovalProcessService approvalProcessService;

    @Mock
    private ApprovalTaskMapper approvalTaskMapper;

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    @Mock
    private ApprovalRecordMapper approvalRecordMapper;

    @Mock
    private TaskAuthorizationService taskAuthorizationService;

    @Mock
    private UserPositionMapper userPositionMapper;

    @Mock
    private OrgService orgService;

    @Mock
    private UserService userService;

    @Mock
    private PositionService positionService;

    @Mock
    private AppService appService;

    @Mock
    private OrgScopeService orgScopeService;

    @Mock
    private Validator validator;

    @Mock
    private UserDisplayService userDisplayService;

    @Mock
    private OperationLogRecorder operationLogRecorder;

    private ApprovalRequestServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 列缓存，覆盖本类实现中构造 {@code LambdaQueryWrapper}/
     *  {@code LambdaUpdateWrapper} 涉及的全部实体类型。 */
    @BeforeAll
    static void primeLambdaColumnCache() {
        primeEntity(ApprovalRequestEntity.class);
        primeEntity(ApprovalTaskEntity.class);
        primeEntity(ProcessInstanceEntity.class);
        primeEntity(ApprovalRecordEntity.class);
        primeEntity(UserPositionEntity.class);
    }

    /** 为单个实体类初始化 Lambda 列缓存。 */
    private static void primeEntity(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "approvalRequestTest");
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }

    /** 构造被测服务与通用桩。 */
    @BeforeEach
    void setUp() {
        service = new ApprovalRequestServiceImpl(
                mapper,
                approvalProcessService,
                approvalTaskMapper,
                processInstanceMapper,
                approvalRecordMapper,
                taskAuthorizationService,
                userPositionMapper,
                orgService,
                userService,
                positionService,
                appService,
                orgScopeService,
                validator,
                userDisplayService,
                operationLogRecorder);
        CurrentUserContext.setUserId(1L);
        lenient().when(validator.validate(any())).thenReturn(Set.of());
    }

    /** 清理线程上下文。 */
    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    /** 提交时应创建申请并启动流程，不执行真实业务写操作。 */
    @Test
    void submit_shouldCreatePendingRequest_whenSwitchEnabled() {
        AppCreateRequest request = buildAppCreateRequest();
        when(orgScopeService.isOrgIdAllowed(1L, 100L)).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<ApprovalRequestEntity>getArgument(0).setId(10L);
            return 1;
        }).when(mapper).insert(any(ApprovalRequestEntity.class));
        when(approvalProcessService.start(eq(10L), eq(FormFieldBizType.APP), eq(1L), any()))
                .thenReturn(new WorkflowInstanceResult(PROCESS_INSTANCE_ID, "flow-1", "deptLeaderApprove", "部门负责人审批"));

        WriteOperationResultVO<?> result = service.submit(
                FormFieldBizType.APP,
                ApprovalOperationType.CREATE,
                null,
                request);

        assertThat(result.isApprovalEnabled()).isTrue();
        assertThat(result.getApprovalRequest().getId()).isEqualTo(10L);
        assertThat(result.getApprovalRequest().getCurrentNodeName()).isEqualTo("部门负责人审批");
        verify(appService, never()).create(any());
        verify(approvalProcessService).start(10L, FormFieldBizType.APP, 1L, null);
    }

    /**
     * 提交请求结构校验失败时不得创建审批记录或启动流程。
     */
    @Test
    void submit_shouldRejectInvalidPayload_beforeCreatingRequest() {
        AppCreateRequest request = buildAppCreateRequest();
        @SuppressWarnings("unchecked")
        ConstraintViolation<AppCreateRequest> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("应用名称不能为空");
        when(validator.validate(request)).thenReturn(Set.of(violation));

        assertThatThrownBy(() -> service.submit(
                FormFieldBizType.APP,
                ApprovalOperationType.CREATE,
                null,
                request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用名称不能为空");

        verify(mapper, never()).insert(any(ApprovalRequestEntity.class));
        verify(approvalProcessService, never()).start(any(), any(), any(), any());
    }

    /**
     * 提交人的管辖范围不包含所属组织时不得创建审批记录或启动流程。
     */
    @Test
    void submit_shouldRejectOutOfScopeRequest_beforeCreatingRequest() {
        AppCreateRequest request = buildAppCreateRequest();
        when(orgScopeService.isOrgIdAllowed(1L, 100L)).thenReturn(false);

        assertThatThrownBy(() -> service.submit(
                FormFieldBizType.APP,
                ApprovalOperationType.CREATE,
                null,
                request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管辖范围");

        verify(mapper, never()).insert(any(ApprovalRequestEntity.class));
        verify(approvalProcessService, never()).start(any(), any(), any(), any());
    }

    /**
     * 四类业务对象的五种操作均应只创建审批申请，不在提交阶段执行原业务写操作。
     *
     * @param bizType       业务对象类型
     * @param operationType 操作类型
     */
    @ParameterizedTest(name = "{0}-{1}")
    @MethodSource("submitCases")
    void submit_shouldSupportAllBizAndOperationCombinations(
            String bizType,
            String operationType) {
        Long targetId = ApprovalOperationType.CREATE.equals(operationType) ? null : 99L;
        Object payload = buildPayload(bizType, operationType);
        lenient().when(orgScopeService.isOrgIdAllowed(1L, 100L)).thenReturn(true);
        lenient().when(orgScopeService.isOrgIdAllowed(1L, 99L)).thenReturn(true);
        prepareCurrentTarget(bizType, targetId);
        doAnswer(invocation -> {
            invocation.<ApprovalRequestEntity>getArgument(0).setId(10L);
            return 1;
        }).when(mapper).insert(any(ApprovalRequestEntity.class));
        when(approvalProcessService.start(eq(10L), eq(bizType), anyLong(), any()))
                .thenReturn(new WorkflowInstanceResult(PROCESS_INSTANCE_ID, "flow-1", "deptLeaderApprove", "部门负责人审批"));

        WriteOperationResultVO<?> result = service.submit(bizType, operationType, targetId, payload);

        assertThat(result.isApprovalEnabled()).isTrue();
        assertThat(result.getApprovalRequest().getId()).isEqualTo(10L);
        verify(approvalProcessService).start(eq(10L), eq(bizType), eq(1L), any());
    }

    /** 非最终节点通过时应仅推进流程、更新当前节点名称，不执行任何业务写操作。 */
    @Test
    void approve_shouldOnlyAdvanceNode_whenNotFinalNode() {
        ApprovalRequestEntity entity = buildPendingEntity();
        when(mapper.selectById(10L)).thenReturn(entity);
        when(approvalTaskMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(ApprovalTaskEntity.builder().id(TASK_ID).processInstanceId(PROCESS_INSTANCE_ID)
                        .status(TaskStatus.PENDING).build());
        when(processInstanceMapper.selectById(PROCESS_INSTANCE_ID)).thenReturn(ProcessInstanceEntity.builder()
                .id(PROCESS_INSTANCE_ID).status(ProcessInstanceStatus.RUNNING).currentNodeName("安全管理员审批").build());
        when(mapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        CurrentUserContext.setUserId(2L);

        service.approve(10L, "同意");

        verify(approvalProcessService).approve(TASK_ID, 2L, "同意");
        verify(appService, never()).create(any());
        verify(orgScopeService, never()).isOrgIdAllowed(any(), any());
        ArgumentCaptor<LambdaUpdateWrapper<ApprovalRequestEntity>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(eq(null), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("currentNodeName");
        assertThat(captor.getValue().getSqlSet()).doesNotContain("status");
    }

    /** 审批到最终节点通过时应以提交人身份校验范围并执行业务方法，申请状态置为已通过。 */
    @Test
    void approve_shouldExecuteAsSubmitterAndFinalizeApproval_whenFinalNode() {
        ApprovalRequestEntity entity = buildPendingEntity();
        AppVO created = AppVO.builder().id(20L).name("应用").build();
        when(mapper.selectById(10L)).thenReturn(entity);
        stubOpenTaskAndFinalInstance();
        when(mapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(orgScopeService.isOrgIdAllowed(1L, 100L)).thenReturn(true);
        when(appService.create(any(AppCreateRequest.class))).thenReturn(created);
        CurrentUserContext.setUserId(2L);

        service.approve(10L, "同意");

        verify(orgScopeService).isOrgIdAllowed(1L, 100L);
        verify(appService).create(any(AppCreateRequest.class));
        verify(approvalProcessService).approve(TASK_ID, 2L, "同意");
        assertThat(CurrentUserContext.getUserId()).isEqualTo(2L);
    }

    /**
     * 四类业务对象的创建申请在最终节点审批通过后均应调用对应的既有业务 Service。
     *
     * @param bizType 业务对象类型
     */
    @ParameterizedTest(name = "approve-{0}-CREATE")
    @MethodSource("bizTypes")
    void approve_shouldExecuteExistingCreateService_forEveryBizType(String bizType) {
        Object payload = buildPayload(bizType, ApprovalOperationType.CREATE);
        ApprovalRequestEntity entity = buildPendingEntity(bizType, payload);
        when(mapper.selectById(10L)).thenReturn(entity);
        stubOpenTaskAndFinalInstance();
        when(mapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        lenient().when(orgScopeService.isOrgIdAllowed(1L, 100L)).thenReturn(true);
        prepareCreateResult(bizType);
        CurrentUserContext.setUserId(2L);

        service.approve(10L, "同意");

        verifyCreateCalled(bizType);
        verify(approvalProcessService).approve(TASK_ID, 2L, "同意");
        assertThat(CurrentUserContext.getUserId()).isEqualTo(2L);
    }

    /**
     * 最终节点审批时提交人的管辖范围已收紧，应拒绝执行业务写操作，且不应写入已通过状态。
     */
    @Test
    void approve_shouldReject_whenSubmitterScopeWasTightened() {
        OrgCreateRequest payload = (OrgCreateRequest) buildPayload(
                FormFieldBizType.ORG,
                ApprovalOperationType.CREATE);
        when(mapper.selectById(10L)).thenReturn(buildPendingEntity(FormFieldBizType.ORG, payload));
        stubOpenTaskAndFinalInstance();
        when(orgScopeService.isOrgIdAllowed(1L, 100L)).thenReturn(false);
        CurrentUserContext.setUserId(2L);

        assertThatThrownBy(() -> service.approve(10L, "同意"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管辖范围");

        verify(orgService, never()).create(any());
        verify(mapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        assertThat(CurrentUserContext.getUserId()).isEqualTo(2L);
    }

    /**
     * 用户更新申请在最终节点审批通过后应完整反序列化任职数组并调用既有 diff 更新逻辑入口。
     */
    @Test
    void approve_shouldPreserveUserPositions_forUpdateRequest() {
        UserPositionRequest position = new UserPositionRequest();
        position.setId(1000L);
        position.setOrgId(100L);
        position.setPositionType("primary");
        UserUpdateRequest payload = new UserUpdateRequest();
        payload.setName("更新用户");
        payload.setCode("U001");
        payload.setPositions(java.util.List.of(position));
        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .id(10L)
                .bizType(FormFieldBizType.USER)
                .operationType(ApprovalOperationType.UPDATE)
                .targetId(30L)
                .requestPayload(JacksonUtils.toJson(payload))
                .status(ApprovalRequestStatus.PENDING)
                .processInstanceId(PROCESS_INSTANCE_ID)
                .createBy("1")
                .createTime(LocalDateTime.now())
                .build();
        when(mapper.selectById(10L)).thenReturn(entity);
        stubOpenTaskAndFinalInstance();
        when(mapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(userService.update(eq(30L), any(UserUpdateRequest.class)))
                .thenReturn(UserVO.builder().id(30L).build());
        CurrentUserContext.setUserId(2L);

        service.approve(10L, "同意");

        ArgumentCaptor<UserUpdateRequest> requestCaptor = ArgumentCaptor.forClass(UserUpdateRequest.class);
        verify(userService).update(eq(30L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getPositions()).hasSize(1);
        assertThat(requestCaptor.getValue().getPositions().get(0).getId()).isEqualTo(1000L);
        assertThat(requestCaptor.getValue().getPositions().get(0).getOrgId()).isEqualTo(100L);
    }

    /** 最终节点业务规则校验失败时不应写入已通过状态，也不应创建业务记录。 */
    @Test
    void approve_shouldNotFinalize_whenBusinessValidationFails() {
        ApprovalRequestEntity entity = buildPendingEntity();
        when(mapper.selectById(10L)).thenReturn(entity);
        stubOpenTaskAndFinalInstance();
        when(orgScopeService.isOrgIdAllowed(1L, 100L)).thenReturn(true);
        when(appService.create(any(AppCreateRequest.class))).thenThrow(new BusinessException("应用编码已存在"));
        CurrentUserContext.setUserId(2L);

        assertThatThrownBy(() -> service.approve(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("编码已存在");
        verify(mapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    /** 已被处理（非待审批状态）的申请不能再次审批通过。 */
    @Test
    void approve_shouldReject_whenRequestAlreadyProcessed() {
        ApprovalRequestEntity entity = buildPendingEntity();
        entity.setStatus(ApprovalRequestStatus.APPROVED);
        when(mapper.selectById(10L)).thenReturn(entity);

        assertThatThrownBy(() -> service.approve(10L, "同意"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被处理");
        verify(approvalProcessService, never()).approve(any(), any(), any());
    }

    /** 拒绝时必须提供非空意见。 */
    @Test
    void reject_shouldRequireOpinion() {
        assertThatThrownBy(() -> service.reject(10L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("拒绝意见");
    }

    /** 拒绝成功时（无论处于第几级）应直接终止流程且不执行任何业务写方法。 */
    @Test
    void reject_shouldTerminateWithoutBusinessWrite() {
        when(mapper.selectById(10L)).thenReturn(buildPendingEntity());
        when(approvalTaskMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(ApprovalTaskEntity.builder().id(TASK_ID).processInstanceId(PROCESS_INSTANCE_ID)
                        .status(TaskStatus.PENDING).build());
        when(mapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        CurrentUserContext.setUserId(2L);

        service.reject(10L, "信息不完整");

        verify(approvalProcessService).reject(TASK_ID, 2L, "信息不完整");
        verify(appService, never()).create(any());
    }

    /** 非提交人不能撤回申请。 */
    @Test
    void cancel_shouldRejectNonSubmitter() {
        when(mapper.selectById(10L)).thenReturn(buildPendingEntity());
        CurrentUserContext.setUserId(2L);

        assertThatThrownBy(() -> service.cancel(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本人");
        verify(approvalProcessService, never()).withdraw(any(), any());
    }

    /** 提交人应能撤回自己尚未有任何一级审批处理记录的待审批申请并终止流程。 */
    @Test
    void cancel_shouldTerminateProcessForSubmitter() {
        when(mapper.selectById(10L)).thenReturn(buildPendingEntity());
        when(mapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);

        service.cancel(10L);

        verify(approvalProcessService).withdraw(PROCESS_INSTANCE_ID, 1L);
    }

    /** 已处理（非待审批状态）的申请不能重复撤回。 */
    @Test
    void cancel_shouldRejectProcessedRequest() {
        ApprovalRequestEntity entity = buildPendingEntity();
        entity.setStatus(ApprovalRequestStatus.APPROVED);
        when(mapper.selectById(10L)).thenReturn(entity);

        assertThatThrownBy(() -> service.cancel(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待审批");
        verify(approvalProcessService, never()).withdraw(any(), any());
    }

    /** 已经过第一级审批、流转到第二级仍处于待审批状态的多级申请不能撤回。 */
    @Test
    void cancel_shouldRejectWhenAlreadyPassedFirstLevel() {
        when(mapper.selectById(10L)).thenReturn(buildPendingEntity());
        doThrow(new BusinessException("流程已存在审批记录，不能撤回"))
                .when(approvalProcessService).withdraw(PROCESS_INSTANCE_ID, 1L);

        assertThatThrownBy(() -> service.cancel(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能撤回");
        verify(mapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    /** "我的申请"查询应包含提交人及三个可选过滤条件。 */
    @Test
    void pageMine_shouldFilterByCurrentUserAndConditions() {
        Page<ApprovalRequestEntity> resultPage = new Page<>(1, 10, 0L);
        resultPage.setRecords(java.util.List.of());
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);
        when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());

        service.pageMine(FormFieldBizType.APP, ApprovalOperationType.UPDATE, ApprovalRequestStatus.PENDING, 1, 10);

        ArgumentCaptor<LambdaQueryWrapper<ApprovalRequestEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("bizType", "operationType", "status", "createBy");
    }

    /** 无当前用户命中的开放任务时，"待我审批"应直接返回空分页，不触发主表查询。 */
    @Test
    void pagePending_shouldReturnEmpty_whenNoAuthorizedTask() {
        PageResult<ApprovalRequestVO> result = service.pagePending(null, null, 1, 10);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        verify(mapper, never()).selectPage(any(), any());
    }

    /** "待我审批"应仅返回当前用户在当前节点被解析为候选人的申请。 */
    @Test
    void pagePending_shouldFilterByAuthorizedCandidateProcessInstances() {
        ApprovalTaskEntity openTask = ApprovalTaskEntity.builder()
                .id(TASK_ID).processInstanceId(PROCESS_INSTANCE_ID).status(TaskStatus.PENDING).build();
        when(approvalTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(openTask));
        when(taskAuthorizationService.isAuthorized(openTask, 1L)).thenReturn(true);
        when(processInstanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                ProcessInstanceEntity.builder().id(PROCESS_INSTANCE_ID).businessType(FormFieldBizType.APP)
                        .businessId(77L).build()));
        Page<ApprovalRequestEntity> resultPage = new Page<>(1, 10, 0L);
        resultPage.setRecords(java.util.List.of());
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);
        when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());

        service.pagePending(null, null, 1, 10);

        ArgumentCaptor<LambdaQueryWrapper<ApprovalRequestEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("status");
    }

    /**
     * 更新类申请查询结果应同时包含请求新值与目标记录当前值。
     */
    @Test
    void pageMine_shouldIncludeCurrentTargetSnapshot_forUpdateRequest() {
        AppUpdateRequest payload = new AppUpdateRequest();
        payload.setName("新名称");
        payload.setOrgId(100L);
        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .id(10L)
                .bizType(FormFieldBizType.APP)
                .operationType(ApprovalOperationType.UPDATE)
                .targetId(99L)
                .requestPayload(JacksonUtils.toJson(payload))
                .status(ApprovalRequestStatus.PENDING)
                .createBy("1")
                .createTime(LocalDateTime.now())
                .build();
        Page<ApprovalRequestEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(java.util.List.of(entity));
        AppVO current = AppVO.builder().id(99L).name("旧名称").orgId(100L).build();
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);
        when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());
        when(appService.getById(99L)).thenReturn(current);

        PageResult<ApprovalRequestVO> result =
                service.pageMine(FormFieldBizType.APP, ApprovalOperationType.UPDATE, null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getRequestPayload()).isInstanceOf(Map.class);
        assertThat(result.getRecords().get(0).getTargetSnapshot()).isSameAs(current);
    }

    /** 为审批相关测试统一桩出"命中开放任务 + 流程实例已到达最终已通过状态"。 */
    private void stubOpenTaskAndFinalInstance() {
        when(approvalTaskMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(ApprovalTaskEntity.builder().id(TASK_ID).processInstanceId(PROCESS_INSTANCE_ID)
                        .status(TaskStatus.PENDING).build());
        when(processInstanceMapper.selectById(PROCESS_INSTANCE_ID)).thenReturn(ProcessInstanceEntity.builder()
                .id(PROCESS_INSTANCE_ID).status(ProcessInstanceStatus.APPROVED).build());
    }

    /** 构造合法应用创建请求。 */
    private AppCreateRequest buildAppCreateRequest() {
        AppCreateRequest request = new AppCreateRequest();
        request.setName("应用");
        request.setCode("APP_001");
        request.setOwnerId(1L);
        request.setOrgId(100L);
        request.setShowOrder(0);
        return request;
    }

    /**
     * 构造参数化提交测试所需的请求体。
     *
     * @param bizType       业务对象类型
     * @param operationType 操作类型
     * @return 创建/更新请求体，状态类操作返回 {@code null}
     */
    private Object buildPayload(String bizType, String operationType) {
        if (!ApprovalOperationType.CREATE.equals(operationType)
                && !ApprovalOperationType.UPDATE.equals(operationType)) {
            return null;
        }
        return switch (bizType + ':' + operationType) {
            case FormFieldBizType.ORG + ":" + ApprovalOperationType.CREATE -> {
                OrgCreateRequest request = new OrgCreateRequest();
                request.setParentId(100L);
                yield request;
            }
            case FormFieldBizType.ORG + ":" + ApprovalOperationType.UPDATE -> {
                OrgUpdateRequest request = new OrgUpdateRequest();
                request.setParentId(100L);
                yield request;
            }
            case FormFieldBizType.USER + ":" + ApprovalOperationType.CREATE -> new UserCreateRequest();
            case FormFieldBizType.USER + ":" + ApprovalOperationType.UPDATE -> new UserUpdateRequest();
            case FormFieldBizType.POSITION + ":" + ApprovalOperationType.CREATE -> {
                PositionCreateRequest request = new PositionCreateRequest();
                request.setOrgId(100L);
                yield request;
            }
            case FormFieldBizType.POSITION + ":" + ApprovalOperationType.UPDATE -> {
                PositionUpdateRequest request = new PositionUpdateRequest();
                request.setOrgId(100L);
                yield request;
            }
            case FormFieldBizType.APP + ":" + ApprovalOperationType.CREATE -> buildAppCreateRequest();
            case FormFieldBizType.APP + ":" + ApprovalOperationType.UPDATE -> {
                AppUpdateRequest request = new AppUpdateRequest();
                request.setOrgId(100L);
                yield request;
            }
            default -> throw new IllegalArgumentException("不支持的测试参数");
        };
    }

    /**
     * 为非创建操作准备目标记录当前值。
     *
     * @param bizType 业务对象类型
     * @param targetId 目标记录 id
     */
    private void prepareCurrentTarget(String bizType, Long targetId) {
        if (targetId == null) {
            return;
        }
        switch (bizType) {
            case FormFieldBizType.ORG ->
                    lenient().when(orgService.getById(targetId))
                            .thenReturn(OrgVO.builder().id(targetId).parentId(100L).build());
            case FormFieldBizType.POSITION ->
                    lenient().when(positionService.getById(targetId))
                            .thenReturn(PositionVO.builder().id(targetId).orgId(100L).build());
            case FormFieldBizType.APP ->
                    lenient().when(appService.getById(targetId))
                            .thenReturn(AppVO.builder().id(targetId).orgId(100L).build());
            default -> {
                // 用户申请按已确认设计不做管辖组织范围校验，无需读取目标记录。
            }
        }
    }

    /**
     * 提供四类业务对象与五种操作的笛卡尔积。
     *
     * @return 参数流
     */
    private static Stream<Arguments> submitCases() {
        return Stream.of(
                FormFieldBizType.ORG,
                FormFieldBizType.USER,
                FormFieldBizType.POSITION,
                FormFieldBizType.APP)
                .flatMap(bizType -> Stream.of(
                        ApprovalOperationType.CREATE,
                        ApprovalOperationType.UPDATE,
                        ApprovalOperationType.ENABLE,
                        ApprovalOperationType.DISABLE,
                        ApprovalOperationType.DELETE)
                        .map(operationType -> Arguments.of(bizType, operationType)));
    }

    /** 构造待审批应用创建申请。 */
    private ApprovalRequestEntity buildPendingEntity() {
        return ApprovalRequestEntity.builder()
                .id(10L)
                .bizType(FormFieldBizType.APP)
                .operationType(ApprovalOperationType.CREATE)
                .requestPayload(JacksonUtils.toJson(buildAppCreateRequest()))
                .status(ApprovalRequestStatus.PENDING)
                .processInstanceId(PROCESS_INSTANCE_ID)
                .createBy("1")
                .createTime(LocalDateTime.now())
                .build();
    }

    /**
     * 构造指定业务对象的待审批创建申请。
     *
     * @param bizType 业务对象类型
     * @param payload 创建请求体
     * @return 待审批申请
     */
    private ApprovalRequestEntity buildPendingEntity(String bizType, Object payload) {
        return ApprovalRequestEntity.builder()
                .id(10L)
                .bizType(bizType)
                .operationType(ApprovalOperationType.CREATE)
                .requestPayload(JacksonUtils.toJson(payload))
                .status(ApprovalRequestStatus.PENDING)
                .processInstanceId(PROCESS_INSTANCE_ID)
                .createBy("1")
                .createTime(LocalDateTime.now())
                .build();
    }

    /**
     * 为指定业务对象准备创建结果。
     *
     * @param bizType 业务对象类型
     */
    private void prepareCreateResult(String bizType) {
        switch (bizType) {
            case FormFieldBizType.ORG ->
                    when(orgService.create(any(OrgCreateRequest.class)))
                            .thenReturn(OrgVO.builder().id(101L).build());
            case FormFieldBizType.USER ->
                    when(userService.create(any(UserCreateRequest.class)))
                            .thenReturn(UserVO.builder().id(102L).build());
            case FormFieldBizType.POSITION ->
                    when(positionService.create(any(PositionCreateRequest.class)))
                            .thenReturn(PositionVO.builder().id(103L).build());
            case FormFieldBizType.APP ->
                    when(appService.create(any(AppCreateRequest.class)))
                            .thenReturn(AppVO.builder().id(104L).build());
            default -> throw new IllegalArgumentException("不支持的测试业务对象");
        }
    }

    /**
     * 校验指定业务对象的既有创建方法已被调用。
     *
     * @param bizType 业务对象类型
     */
    private void verifyCreateCalled(String bizType) {
        switch (bizType) {
            case FormFieldBizType.ORG -> verify(orgService).create(any(OrgCreateRequest.class));
            case FormFieldBizType.USER -> verify(userService).create(any(UserCreateRequest.class));
            case FormFieldBizType.POSITION -> verify(positionService).create(any(PositionCreateRequest.class));
            case FormFieldBizType.APP -> verify(appService).create(any(AppCreateRequest.class));
            default -> throw new IllegalArgumentException("不支持的测试业务对象");
        }
    }

    /**
     * 提供全部审批业务对象类型。
     *
     * @return 业务对象类型参数流
     */
    private static Stream<Arguments> bizTypes() {
        return Stream.of(
                FormFieldBizType.ORG,
                FormFieldBizType.USER,
                FormFieldBizType.POSITION,
                FormFieldBizType.APP)
                .map(Arguments::of);
    }
}
