package cn.nihility.rbac.approval.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import cn.nihility.rbac.approval.execution.MasterDataOperationExecutor;
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
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
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
import cn.nihility.rbac.workflow.dslv2.form.WorkflowFormVersionService;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.FormVersionEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.service.BusinessLockService;
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
    private OrgMapper orgMapper;

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

    @Mock
    private WorkflowFormVersionService workflowFormVersionService;

    @Mock
    private NodeAssigneeRuleMapper nodeAssigneeRuleMapper;

    @Mock
    private BusinessLockService businessLockService;

    /** 被测服务依赖的 ORG/USER/POSITION/APP 主数据写操作公共组件，用真实实例（内部持有本类的
     *  mock Service）构造，保持对 {@code orgService}/{@code userService} 等既有 mock 断言不变
     *  （production-approval-lifecycle change tasks.md 7.3 抽取该组件后的测试适配）。 */
    private MasterDataOperationExecutor masterDataOperationExecutor;

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
        primeEntity(NodeAssigneeRuleEntity.class);
    }

    /**
     * 为单个实体类初始化 Lambda 列缓存。{@code TableInfoHelper} 的缓存是 JVM 静态、按
     * {@code Class} 缓存一次即永久生效，必须显式开启 {@code mapUnderscoreToCamelCase}
     * （与本项目 {@code mybatis/mybatis.conf} 里真实 MyBatis 配置一致），否则用默认关闭该项的
     * {@link Configuration} 生成的 {@code TableInfo} 会把列名错误地缓存成驼峰字段名本身
     * （如 {@code assigneeId} 而非 {@code assignee_id}），一旦在同一 Gradle 测试 JVM 里比真实
     * {@code @SpringBootTest} 上下文更早跑到，会永久污染同一实体后续所有真实集成测试生成的 SQL
     * （fix-approval-zero-task-process-completion change 实施时发现并修复）。
     */
    private static void primeEntity(Class<?> entityClass) {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "approvalRequestTest");
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }

    /** 构造被测服务与通用桩。 */
    @BeforeEach
    void setUp() {
        masterDataOperationExecutor =
                new MasterDataOperationExecutor(orgService, userService, positionService, appService, orgScopeService);
        service = new ApprovalRequestServiceImpl(
                mapper,
                approvalProcessService,
                approvalTaskMapper,
                processInstanceMapper,
                approvalRecordMapper,
                taskAuthorizationService,
                userPositionMapper,
                orgMapper,
                masterDataOperationExecutor,
                validator,
                userDisplayService,
                operationLogRecorder,
                workflowFormVersionService,
                nodeAssigneeRuleMapper,
                businessLockService);
        CurrentUserContext.setUserId(1L);
        lenient().when(validator.validate(any())).thenReturn(Set.of());
        lenient().when(workflowFormVersionService.ensureCurrentVersion(any()))
                .thenReturn(FormVersionEntity.builder().id(1L).build());
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
        stubInsertAssigningIdAndSelectBack(10L);
        stubRunningProcessInstance();
        when(approvalProcessService.start(
                eq(10L), eq(FormFieldBizType.APP), eq(ApprovalOperationType.CREATE), eq(1L), any(), any()))
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
        verify(approvalProcessService).start(
                eq(10L), eq(FormFieldBizType.APP), eq(ApprovalOperationType.CREATE), eq(1L), isNull(), any());
    }

    /**
     * 提交创建申请时应落库本次命中的表单版本 id，且创建操作无"变更前"概念，beforeSnapshot
     * 恒为空（production-approval-lifecycle change tasks.md 5.1）。
     */
    @Test
    void submit_shouldPersistFormVersionAndSkipBeforeSnapshot_forCreateOperation() {
        AppCreateRequest request = buildAppCreateRequest();
        when(orgScopeService.isOrgIdAllowed(1L, 100L)).thenReturn(true);
        when(workflowFormVersionService.ensureCurrentVersion(FormFieldBizType.APP))
                .thenReturn(FormVersionEntity.builder().id(701L).build());
        ArgumentCaptor<ApprovalRequestEntity> captor = ArgumentCaptor.forClass(ApprovalRequestEntity.class);
        doAnswer(invocation -> {
            invocation.<ApprovalRequestEntity>getArgument(0).setId(10L);
            return 1;
        }).when(mapper).insert(captor.capture());
        when(mapper.selectById(10L)).thenAnswer(invocation -> captor.getValue());
        stubRunningProcessInstance();
        when(approvalProcessService.start(
                eq(10L), eq(FormFieldBizType.APP), eq(ApprovalOperationType.CREATE), eq(1L), any(), any()))
                .thenReturn(new WorkflowInstanceResult(PROCESS_INSTANCE_ID, "flow-1", "deptLeaderApprove", "部门负责人审批"));

        service.submit(FormFieldBizType.APP, ApprovalOperationType.CREATE, null, request);

        ApprovalRequestEntity inserted = captor.getValue();
        assertThat(inserted.getFormVersionId()).isEqualTo(701L);
        assertThat(inserted.getBeforeSnapshot()).isNull();
        assertThat(inserted.getAfterSnapshot()).isEqualTo(inserted.getRequestPayload());
    }

    /**
     * 提交更新申请时应把当前目标记录冻结为 beforeSnapshot，与本次提交的 requestPayload
     * （afterSnapshot）区分开（production-approval-lifecycle change tasks.md 5.1）。
     */
    @Test
    void submit_shouldFreezeBeforeSnapshot_forUpdateOperation() {
        OrgUpdateRequest request = new OrgUpdateRequest();
        request.setParentId(100L);
        when(orgScopeService.isOrgIdAllowed(1L, 99L)).thenReturn(true);
        when(orgService.getById(99L)).thenReturn(OrgVO.builder().id(99L).parentId(100L).name("旧组织名").build());
        ArgumentCaptor<ApprovalRequestEntity> captor = ArgumentCaptor.forClass(ApprovalRequestEntity.class);
        doAnswer(invocation -> {
            invocation.<ApprovalRequestEntity>getArgument(0).setId(11L);
            return 1;
        }).when(mapper).insert(captor.capture());
        when(mapper.selectById(11L)).thenAnswer(invocation -> captor.getValue());
        stubRunningProcessInstance();
        when(approvalProcessService.start(
                eq(11L), eq(FormFieldBizType.ORG), eq(ApprovalOperationType.UPDATE), eq(1L), any(), any()))
                .thenReturn(new WorkflowInstanceResult(PROCESS_INSTANCE_ID, "flow-1", "deptLeaderApprove", "部门负责人审批"));

        service.submit(FormFieldBizType.ORG, ApprovalOperationType.UPDATE, 99L, request);

        ApprovalRequestEntity inserted = captor.getValue();
        assertThat(inserted.getBeforeSnapshot()).contains("旧组织名");
        assertThat(inserted.getAfterSnapshot()).isEqualTo(inserted.getRequestPayload());
    }

    /**
     * 审批过程（approve/reject/cancel 全流程）不存在任何修改 requestPayload/afterSnapshot 的
     * 代码路径：申请提交后业务 payload 已冻结，审批节点只能补充意见字段
     * （production-approval-lifecycle change tasks.md 5.2）。本测试断言最终节点通过后
     * requestPayload 仍与提交时完全一致，验证"无入口可改"这一事实，而不是新写冻结逻辑。
     */
    @Test
    void approve_shouldNotMutateRequestPayload_whenFinalNodeApproved() {
        ApprovalRequestEntity entity = buildPendingEntity();
        String originalPayload = entity.getRequestPayload();
        when(mapper.selectById(10L)).thenReturn(entity);
        stubOpenTaskAndFinalInstance();
        when(mapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(orgScopeService.isOrgIdAllowed(1L, 100L)).thenReturn(true);
        when(appService.create(any(AppCreateRequest.class))).thenReturn(AppVO.builder().id(20L).build());
        CurrentUserContext.setUserId(2L);

        service.approve(10L, "同意");

        ArgumentCaptor<LambdaUpdateWrapper<ApprovalRequestEntity>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(eq(null), captor.capture());
        assertThat(captor.getValue().getSqlSet()).doesNotContain("requestPayload").doesNotContain("afterSnapshot");
        assertThat(entity.getRequestPayload()).isEqualTo(originalPayload);
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
        verify(approvalProcessService, never()).start(any(), any(), any(), any(), any(), any());
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
        verify(approvalProcessService, never()).start(any(), any(), any(), any(), any(), any());
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
        stubInsertAssigningIdAndSelectBack(10L);
        stubRunningProcessInstance();
        when(approvalProcessService.start(eq(10L), eq(bizType), eq(operationType), anyLong(), any(), any()))
                .thenReturn(new WorkflowInstanceResult(PROCESS_INSTANCE_ID, "flow-1", "deptLeaderApprove", "部门负责人审批"));

        WriteOperationResultVO<?> result = service.submit(bizType, operationType, targetId, payload);

        assertThat(result.isApprovalEnabled()).isTrue();
        assertThat(result.getApprovalRequest().getId()).isEqualTo(10L);
        verify(approvalProcessService).start(eq(10L), eq(bizType), eq(operationType), eq(1L), any(), any());
    }

    /** 非最终节点通过时应仅推进流程、更新当前节点名称，不执行任何业务写操作。 */
    @Test
    void approve_shouldOnlyAdvanceNode_whenNotFinalNode() {
        ApprovalRequestEntity entity = buildPendingEntity();
        when(mapper.selectById(10L)).thenReturn(entity);
        when(approvalTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(ApprovalTaskEntity.builder().id(TASK_ID).processInstanceId(PROCESS_INSTANCE_ID)
                        .status(TaskStatus.PENDING).build()));
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
        assertThat(captor.getValue().getSqlSet()).contains("current_node_name");
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

    /**
     * 并行分叉场景下同一流程实例同时存在多条开放任务时，旧的"按申请 id 审批"接口无法确定
     * 操作目标，应消歧拒绝并引导调用方改用按 taskId 明确操作的既有接口
     * （production-approval-lifecycle change tasks.md 6.9）。
     */
    @Test
    void approve_shouldRejectAmbiguously_whenMultipleOpenTasksExist() {
        when(mapper.selectById(10L)).thenReturn(buildPendingEntity());
        when(approvalTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                ApprovalTaskEntity.builder().id(TASK_ID).processInstanceId(PROCESS_INSTANCE_ID)
                        .status(TaskStatus.PENDING).build(),
                ApprovalTaskEntity.builder().id(TASK_ID + 1).processInstanceId(PROCESS_INSTANCE_ID)
                        .status(TaskStatus.PENDING).build()));

        assertThatThrownBy(() -> service.approve(10L, "同意"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多个待处理任务")
                .hasMessageContaining("/api/v1/workflow/tasks/{taskId}/approve");
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
        when(approvalTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(ApprovalTaskEntity.builder().id(TASK_ID).processInstanceId(PROCESS_INSTANCE_ID)
                        .status(TaskStatus.PENDING).build()));
        when(mapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        CurrentUserContext.setUserId(2L);

        service.reject(10L, "信息不完整");

        verify(approvalProcessService).reject(TASK_ID, 2L, "信息不完整");
        verify(appService, never()).create(any());
    }

    /**
     * 拒绝操作同样需要消歧：并行分叉场景下同一流程实例同时存在多条开放任务时应拒绝，而不是
     * 任取其一（production-approval-lifecycle change tasks.md 6.9）。
     */
    @Test
    void reject_shouldRejectAmbiguously_whenMultipleOpenTasksExist() {
        when(mapper.selectById(10L)).thenReturn(buildPendingEntity());
        when(approvalTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                ApprovalTaskEntity.builder().id(TASK_ID).processInstanceId(PROCESS_INSTANCE_ID)
                        .status(TaskStatus.PENDING).build(),
                ApprovalTaskEntity.builder().id(TASK_ID + 1).processInstanceId(PROCESS_INSTANCE_ID)
                        .status(TaskStatus.CLAIMED).build()));
        CurrentUserContext.setUserId(2L);

        assertThatThrownBy(() -> service.reject(10L, "信息不完整"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多个待处理任务")
                .hasMessageContaining("/api/v1/workflow/tasks/{taskId}/reject");
        verify(approvalProcessService, never()).reject(any(), any(), any());
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
                .contains("biz_type", "operation_type", "status", "create_by");
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

    /**
     * ENABLE/DISABLE/DELETE 三类操作查询结果应同样携带目标记录当前值，供前端解析出可展示的
     * 审批对象名称（approval-target-display-name change design.md Decision 1）。
     *
     * @param operationType 操作类型
     */
    @ParameterizedTest(name = "pageMine-{0}-includesTargetSnapshot")
    @MethodSource("targetSnapshotOperationTypes")
    void pageMine_shouldIncludeCurrentTargetSnapshot_forEnableDisableDeleteRequest(String operationType) {
        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .id(10L)
                .bizType(FormFieldBizType.APP)
                .operationType(operationType)
                .targetId(99L)
                .status(ApprovalRequestStatus.PENDING)
                .createBy("1")
                .createTime(LocalDateTime.now())
                .build();
        Page<ApprovalRequestEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(java.util.List.of(entity));
        AppVO current = AppVO.builder().id(99L).name("当前名称").orgId(100L).build();
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);
        when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());
        when(appService.getById(99L)).thenReturn(current);

        PageResult<ApprovalRequestVO> result =
                service.pageMine(FormFieldBizType.APP, operationType, null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTargetSnapshot()).isSameAs(current);
    }

    /**
     * DELETE 申请审批通过、目标记录已被物理删除后再次查询该申请，接口应正常返回且
     * {@code targetSnapshot} 为空，不抛异常（{@code getCurrentTarget()} 既有的"捕获异常返回
     * {@code null}"兜底在放宽后依旧生效，approval-target-display-name change tasks.md 2.2）。
     */
    @Test
    void pageMine_shouldReturnEmptySnapshot_whenDeleteTargetAlreadyRemoved() {
        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .id(10L)
                .bizType(FormFieldBizType.APP)
                .operationType(ApprovalOperationType.DELETE)
                .targetId(99L)
                .status(ApprovalRequestStatus.APPROVED)
                .createBy("1")
                .createTime(LocalDateTime.now())
                .build();
        Page<ApprovalRequestEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(java.util.List.of(entity));
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);
        when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());
        when(appService.getById(99L)).thenThrow(new BusinessException("应用不存在"));

        PageResult<ApprovalRequestVO> result =
                service.pageMine(FormFieldBizType.APP, ApprovalOperationType.DELETE, null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTargetSnapshot()).isNull();
    }

    /**
     * {@code bizType=USER} 的 CREATE 申请，{@code requestPayload.positions} 携带任职记录时，
     * 查询结果中每条记录都应带上批量查出的组织名称（approval-target-display-name change
     * tasks.md 2.4）。
     */
    @Test
    void pageMine_shouldEnrichPositionOrgNames_forUserCreateRequest() {
        UserPositionRequest position1 = new UserPositionRequest();
        position1.setOrgId(100L);
        position1.setPositionType("primary");
        UserPositionRequest position2 = new UserPositionRequest();
        position2.setOrgId(200L);
        position2.setPositionType("secondary");
        UserCreateRequest payload = new UserCreateRequest();
        payload.setName("新用户");
        payload.setCode("U002");
        payload.setPositions(java.util.List.of(position1, position2));
        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .id(10L)
                .bizType(FormFieldBizType.USER)
                .operationType(ApprovalOperationType.CREATE)
                .requestPayload(JacksonUtils.toJson(payload))
                .status(ApprovalRequestStatus.PENDING)
                .createBy("1")
                .createTime(LocalDateTime.now())
                .build();
        Page<ApprovalRequestEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(java.util.List.of(entity));
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);
        when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());
        when(orgMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                OrgEntity.builder().id(100L).name("组织甲").build(),
                OrgEntity.builder().id(200L).name("组织乙").build()));

        PageResult<ApprovalRequestVO> result =
                service.pageMine(FormFieldBizType.USER, ApprovalOperationType.CREATE, null, 1, 10);

        @SuppressWarnings("unchecked")
        Map<String, Object> returnedPayload = (Map<String, Object>) result.getRecords().get(0).getRequestPayload();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> positions = (List<Map<String, Object>>) returnedPayload.get("positions");
        assertThat(positions).hasSize(2);
        assertThat(positions.get(0)).containsEntry("orgName", "组织甲");
        assertThat(positions.get(1)).containsEntry("orgName", "组织乙");
    }

    /**
     * {@code requestPayload.positions} 中某条记录的 {@code orgId} 指向一个不存在的组织时，
     * 该条记录 {@code orgName} 应为空，其余记录不受影响，接口不报错（approval-target-display-name
     * change tasks.md 2.5）。
     */
    @Test
    void pageMine_shouldSkipOrgName_whenPositionOrgIdNotFound() {
        UserPositionRequest position1 = new UserPositionRequest();
        position1.setOrgId(100L);
        position1.setPositionType("primary");
        UserPositionRequest position2 = new UserPositionRequest();
        position2.setOrgId(999L);
        position2.setPositionType("secondary");
        UserCreateRequest payload = new UserCreateRequest();
        payload.setName("新用户");
        payload.setCode("U003");
        payload.setPositions(java.util.List.of(position1, position2));
        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .id(10L)
                .bizType(FormFieldBizType.USER)
                .operationType(ApprovalOperationType.CREATE)
                .requestPayload(JacksonUtils.toJson(payload))
                .status(ApprovalRequestStatus.PENDING)
                .createBy("1")
                .createTime(LocalDateTime.now())
                .build();
        Page<ApprovalRequestEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(java.util.List.of(entity));
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);
        when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());
        when(orgMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(OrgEntity.builder().id(100L).name("组织甲").build()));

        PageResult<ApprovalRequestVO> result =
                service.pageMine(FormFieldBizType.USER, ApprovalOperationType.CREATE, null, 1, 10);

        @SuppressWarnings("unchecked")
        Map<String, Object> returnedPayload = (Map<String, Object>) result.getRecords().get(0).getRequestPayload();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> positions = (List<Map<String, Object>>) returnedPayload.get("positions");
        assertThat(positions).hasSize(2);
        assertThat(positions.get(0)).containsEntry("orgName", "组织甲");
        assertThat(positions.get(1)).doesNotContainKey("orgName");
    }

    /**
     * 查询结果应携带 {@code processInstanceId}，供前端据此查询流程实例详情
     * （add-approval-remark-and-process-flowchart change tasks.md 2.1，此前 {@code toVO}
     * 转换遗漏了该字段的暴露）。
     */
    @Test
    void pageMine_shouldExposeProcessInstanceId() {
        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .id(10L)
                .bizType(FormFieldBizType.APP)
                .operationType(ApprovalOperationType.CREATE)
                .requestPayload(JacksonUtils.toJson(buildAppCreateRequest()))
                .status(ApprovalRequestStatus.PENDING)
                .processInstanceId(PROCESS_INSTANCE_ID)
                .createBy("1")
                .createTime(LocalDateTime.now())
                .build();
        Page<ApprovalRequestEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(java.util.List.of(entity));
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);
        when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());

        PageResult<ApprovalRequestVO> result =
                service.pageMine(FormFieldBizType.APP, ApprovalOperationType.CREATE, null, 1, 10);

        assertThat(result.getRecords().get(0).getProcessInstanceId()).isEqualTo(PROCESS_INSTANCE_ID);
    }

    /**
     * 待审批申请当前所处节点配置了 HIDDEN 字段权限时，返回给前端的 requestPayload 应整条
     * 移除该字段，而不是设为 null（production-approval-lifecycle change tasks.md 5.2）。
     */
    @Test
    void pageMine_shouldRemoveHiddenFields_whenCurrentNodeConfiguresFieldPermissions() {
        AppCreateRequest payload = buildAppCreateRequest();
        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .id(10L)
                .bizType(FormFieldBizType.APP)
                .operationType(ApprovalOperationType.CREATE)
                .requestPayload(JacksonUtils.toJson(payload))
                .status(ApprovalRequestStatus.PENDING)
                .processInstanceId(PROCESS_INSTANCE_ID)
                .createBy("1")
                .createTime(LocalDateTime.now())
                .build();
        Page<ApprovalRequestEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(java.util.List.of(entity));
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);
        when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());
        when(processInstanceMapper.selectById(PROCESS_INSTANCE_ID)).thenReturn(ProcessInstanceEntity.builder()
                .id(PROCESS_INSTANCE_ID).processDefinitionId(50L).currentNodeId("leader").build());
        when(nodeAssigneeRuleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(
                cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity.builder()
                        .id(1L)
                        .processDefinitionId(50L)
                        .nodeId("leader")
                        .fieldPermissionsJson(JacksonUtils.toJson(Map.of("ownerId", "HIDDEN", "name", "READ")))
                        .build());

        PageResult<ApprovalRequestVO> result =
                service.pageMine(FormFieldBizType.APP, ApprovalOperationType.CREATE, null, 1, 10);

        @SuppressWarnings("unchecked")
        Map<String, Object> returnedPayload = (Map<String, Object>) result.getRecords().get(0).getRequestPayload();
        assertThat(returnedPayload).doesNotContainKey("ownerId");
        assertThat(returnedPayload).containsKey("name");
    }

    /**
     * 申请人本人调用 {@code getDetail} 能正常查看自己提交的申请详情
     * （approval-history-detail-entry change tasks.md 2.1）。
     */
    @Test
    void getDetail_shouldReturnDetail_forApplicantSelf() {
        ApprovalRequestEntity entity = buildPendingEntity();
        when(mapper.selectById(10L)).thenReturn(entity);
        when(processInstanceMapper.selectById(PROCESS_INSTANCE_ID)).thenReturn(ProcessInstanceEntity.builder()
                .id(PROCESS_INSTANCE_ID).applicantId(1L).build());
        when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of("1", "张三"));

        ApprovalRequestVO vo = service.getDetail(10L, 1L);

        assertThat(vo.getId()).isEqualTo(10L);
        assertThat(vo.getCreateByName()).isEqualTo("张三");
        verify(approvalRecordMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 历史处理过该申请关联流程实例的审批人（{@code ApprovalRecordEntity.operatorId} 命中）
     * 能正常查看，即使当前不再是任何开放任务的候选人（approval-history-detail-entry change
     * tasks.md 2.2）。
     */
    @Test
    void getDetail_shouldReturnDetail_forHistoricalApprover() {
        ApprovalRequestEntity entity = buildPendingEntity();
        when(mapper.selectById(10L)).thenReturn(entity);
        when(processInstanceMapper.selectById(PROCESS_INSTANCE_ID)).thenReturn(ProcessInstanceEntity.builder()
                .id(PROCESS_INSTANCE_ID).applicantId(999L).build());
        when(approvalRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                ApprovalRecordEntity.builder().processInstanceId(PROCESS_INSTANCE_ID).operatorId(77L).build()));
        when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());

        ApprovalRequestVO vo = service.getDetail(10L, 77L);

        assertThat(vo.getId()).isEqualTo(10L);
    }

    /**
     * 当前所处审批节点的候选人（尚未处理，任务仍 {@code PENDING}/{@code CLAIMED}）能正常查看
     * （approval-history-detail-entry change tasks.md 2.3）。
     */
    @Test
    void getDetail_shouldReturnDetail_forCurrentOpenTaskCandidate() {
        ApprovalRequestEntity entity = buildPendingEntity();
        ApprovalTaskEntity openTask = ApprovalTaskEntity.builder()
                .id(TASK_ID).processInstanceId(PROCESS_INSTANCE_ID).status(TaskStatus.PENDING).build();
        when(mapper.selectById(10L)).thenReturn(entity);
        when(processInstanceMapper.selectById(PROCESS_INSTANCE_ID)).thenReturn(ProcessInstanceEntity.builder()
                .id(PROCESS_INSTANCE_ID).applicantId(999L).build());
        when(approvalRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(approvalTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(openTask));
        when(taskAuthorizationService.isAuthorized(openTask, 88L)).thenReturn(true);
        when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());

        ApprovalRequestVO vo = service.getDetail(10L, 88L);

        assertThat(vo.getId()).isEqualTo(10L);
    }

    /**
     * 与申请无任何参与关系的用户调用接口被拒绝，返回无权限错误
     * （approval-history-detail-entry change tasks.md 2.4）。
     */
    @Test
    void getDetail_shouldReject_whenViewerHasNoParticipation() {
        ApprovalRequestEntity entity = buildPendingEntity();
        ApprovalTaskEntity openTask = ApprovalTaskEntity.builder()
                .id(TASK_ID).processInstanceId(PROCESS_INSTANCE_ID).status(TaskStatus.PENDING).build();
        when(mapper.selectById(10L)).thenReturn(entity);
        when(processInstanceMapper.selectById(PROCESS_INSTANCE_ID)).thenReturn(ProcessInstanceEntity.builder()
                .id(PROCESS_INSTANCE_ID).applicantId(999L).build());
        when(approvalRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(approvalTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(openTask));
        when(taskAuthorizationService.isAuthorized(openTask, 66L)).thenReturn(false);

        assertThatThrownBy(() -> service.getDetail(10L, 66L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限");
    }

    /**
     * {@code processInstanceId} 为空的申请，提交人和审批人能查看，其余用户被拒绝
     * （approval-history-detail-entry change tasks.md 2.5）。
     */
    @Test
    void getDetail_shouldFallbackToSubmitterOrApprover_whenProcessInstanceIdIsNull() {
        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .id(10L)
                .bizType(FormFieldBizType.APP)
                .operationType(ApprovalOperationType.CREATE)
                .requestPayload(JacksonUtils.toJson(buildAppCreateRequest()))
                .status(ApprovalRequestStatus.APPROVED)
                .approverId(5L)
                .createBy("1")
                .createTime(LocalDateTime.now())
                .build();
        when(mapper.selectById(10L)).thenReturn(entity);
        lenient().when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());

        assertThat(service.getDetail(10L, 1L).getId()).isEqualTo(10L);
        assertThat(service.getDetail(10L, 5L).getId()).isEqualTo(10L);
        assertThatThrownBy(() -> service.getDetail(10L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限");
        verify(processInstanceMapper, never()).selectById(any());
    }

    /**
     * 查询一个不存在的 {@code id} 返回"申请不存在"错误，不是无权限错误
     * （approval-history-detail-entry change tasks.md 2.6）。
     */
    @Test
    void getDetail_shouldThrowNotFound_whenRequestDoesNotExist() {
        when(mapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getDetail(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("申请不存在");
    }

    /**
     * 返回的 {@code ApprovalRequestVO} 内容（{@code requestPayload}/{@code targetSnapshot}/
     * 审批对象名称等）与 {@code pageMine}/{@code pagePending} 对同一条记录返回的内容一致，
     * 因为复用同一个 {@code toVO()}（approval-history-detail-entry change tasks.md 2.7）。
     */
    @Test
    void getDetail_shouldReturnSameContentAsPageMine_forSameRecord() {
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
                .approverId(5L)
                .createBy("1")
                .createTime(LocalDateTime.now())
                .build();
        AppVO current = AppVO.builder().id(99L).name("旧名称").orgId(100L).build();
        when(mapper.selectById(10L)).thenReturn(entity);
        lenient().when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of("1", "张三", "5", "李四"));
        lenient().when(appService.getById(99L)).thenReturn(current);
        Page<ApprovalRequestEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(java.util.List.of(entity));
        lenient().when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);

        ApprovalRequestVO detailVO = service.getDetail(10L, 1L);
        ApprovalRequestVO pageVO = service.pageMine(FormFieldBizType.APP, ApprovalOperationType.UPDATE, null, 1, 10)
                .getRecords().get(0);

        assertThat(detailVO.getRequestPayload()).isEqualTo(pageVO.getRequestPayload());
        assertThat(detailVO.getTargetSnapshot()).isEqualTo(pageVO.getTargetSnapshot());
        assertThat(detailVO.getApproverName()).isEqualTo(pageVO.getApproverName());
        assertThat(detailVO.getCreateByName()).isEqualTo(pageVO.getCreateByName());
        assertThat(detailVO.getBizType()).isEqualTo(pageVO.getBizType());
        assertThat(detailVO.getOperationType()).isEqualTo(pageVO.getOperationType());
    }

    /** 为审批相关测试统一桩出"命中唯一开放任务 + 流程实例已到达最终已通过状态"。 */
    private void stubOpenTaskAndFinalInstance() {
        when(approvalTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(ApprovalTaskEntity.builder().id(TASK_ID).processInstanceId(PROCESS_INSTANCE_ID)
                        .status(TaskStatus.PENDING).build()));
        when(processInstanceMapper.selectById(PROCESS_INSTANCE_ID)).thenReturn(ProcessInstanceEntity.builder()
                .id(PROCESS_INSTANCE_ID).status(ProcessInstanceStatus.APPROVED).build());
    }

    /**
     * 为 {@code submit()} 相关测试桩流程实例仍处于 {@code RUNNING} 状态（正常有开放任务的既有
     * 场景，fix-approval-zero-task-process-completion change tasks.md 2.5 回归覆盖）：
     * {@code submit()} 内新增的"按流程实例真实状态分支处理"逻辑依赖此桩，否则默认返回
     * {@code null} 会被当作"流程实例不存在"而拒绝。
     */
    private void stubRunningProcessInstance() {
        when(processInstanceMapper.selectById(PROCESS_INSTANCE_ID)).thenReturn(ProcessInstanceEntity.builder()
                .id(PROCESS_INSTANCE_ID).status(ProcessInstanceStatus.RUNNING).build());
    }

    /**
     * 桩 {@code mapper.insert(...)} 写回自增主键，并令后续 {@code mapper.selectById(id)}
     * 返回同一个（可能已被 {@code submit()} 内部继续原地修改字段的）实体对象，模拟
     * {@code submit()} 末尾重新查询最新状态的行为（fix-approval-zero-task-process-completion
     * change design.md Decision 2 要点三）。
     *
     * @param id 桩定的自增主键
     */
    private void stubInsertAssigningIdAndSelectBack(Long id) {
        ApprovalRequestEntity[] holder = new ApprovalRequestEntity[1];
        doAnswer(invocation -> {
            ApprovalRequestEntity inserted = invocation.getArgument(0);
            inserted.setId(id);
            holder[0] = inserted;
            return 1;
        }).when(mapper).insert(any(ApprovalRequestEntity.class));
        when(mapper.selectById(id)).thenAnswer(invocation -> holder[0]);
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

    /**
     * 提供需要填充 {@code targetSnapshot} 的三类"targetId 非空但不携带 requestPayload"操作类型。
     *
     * @return 操作类型参数流
     */
    private static Stream<Arguments> targetSnapshotOperationTypes() {
        return Stream.of(ApprovalOperationType.ENABLE, ApprovalOperationType.DISABLE, ApprovalOperationType.DELETE)
                .map(Arguments::of);
    }
}
