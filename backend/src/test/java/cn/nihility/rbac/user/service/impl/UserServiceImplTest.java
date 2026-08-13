package cn.nihility.rbac.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.dict.service.DictItemService;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.formfield.support.FormFieldSnapshotSupport;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.sync.event.DomainEventPublisher;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.dto.UserPositionRequest;
import cn.nihility.rbac.user.dto.UserUpdateRequest;
import cn.nihility.rbac.user.dto.UserVO;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.service.UserDisplayService;
import cn.nihility.rbac.user.service.support.PositionDynamicFieldSupport;
import cn.nihility.rbac.user.service.support.PositionLogSnapshotSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserServiceImpl} 的单元测试，重点覆盖编号/身份证号唯一性校验范围、
 * 任职记录整体更新的增量 diff（更新保留创建审计/新增/删除/清空/拒绝他人记录）、
 * 操作日志记录调用等分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    /** 被测服务的用户数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /** 被测服务的用户任职记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserPositionMapper userPositionMapper;

    /** 被测服务的组织数据访问依赖，用于回填任职记录的所属组织名称，使用 Mockito 打桩。 */
    @Mock
    private OrgMapper orgMapper;

    /** 被测服务的操作日志记录组件依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogRecorder operationLogRecorder;

    /** 被测服务的表单字段定义业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private FormFieldDefinitionService formFieldDefinitionService;

    /** 被测服务的操作日志扩展字段快照填充依赖，使用 Mockito 打桩。 */
    @Mock
    private FormFieldSnapshotSupport formFieldSnapshotSupport;

    /** 被测服务的字典项业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private DictItemService dictItemService;

    /** 被测服务的密码业务逻辑依赖（{@code auth} 模块暴露），使用 Mockito 打桩。 */
    @Mock
    private PasswordService passwordService;

    /** 被测服务的管辖组织范围解析依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgScopeService orgScopeService;

    /** 被测服务的当前登录操作人用户 id 解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务的审计字段展示名批量解析依赖，使用 Mockito 打桩。 */
    @Mock
    private UserDisplayService userDisplayService;

    /** 被测服务的数据变更事件发布依赖，使用 Mockito 打桩。 */
    @Mock
    private DomainEventPublisher domainEventPublisher;

    /** 被测服务实例。 */
    private UserServiceImpl userService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过 {@code UserConvert.INSTANCE}
     * 静态调用完成，无需在此注入或 mock。动态字段定义默认桩为空列表，身份证号相关
     * 用例会按需覆盖该桩，模拟默认字段定义（isUnique=true）驱动的唯一性校验。
     * {@link PositionDynamicFieldSupport} 直接用已打桩的
     * {@code formFieldDefinitionService}/{@code userPositionMapper} 构造真实实例，不额外 mock；
     * {@link PositionLogSnapshotSupport} 同理，直接用已打桩的
     * {@code userMapper}/{@code orgMapper}/{@code formFieldDefinitionService}/
     * {@code formFieldSnapshotSupport}/{@code dictItemService} 构造真实实例，
     * 用于验证 {@code syncPositions} 新增/更新/物理删除分支追加的操作日志记录调用。
     * 管辖组织范围默认桩为 {@code Optional.empty()}（不受限制），受限场景在下方单独的
     * 用例中覆盖（user-org-scope-data-permission change）。
     */
    @BeforeEach
    void setUp() {
        PositionDynamicFieldSupport positionDynamicFieldSupport =
                new PositionDynamicFieldSupport(formFieldDefinitionService, userPositionMapper);
        PositionLogSnapshotSupport positionLogSnapshotSupport = new PositionLogSnapshotSupport(userMapper, orgMapper,
                formFieldDefinitionService, formFieldSnapshotSupport, dictItemService);
        userService = new UserServiceImpl(userMapper, userPositionMapper, orgMapper, operationLogRecorder,
                formFieldDefinitionService, formFieldSnapshotSupport, dictItemService, positionDynamicFieldSupport,
                positionLogSnapshotSupport, passwordService, orgScopeService, currentOperatorService,
                userDisplayService, domainEventPublisher);
        lenient().when(orgMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        lenient().when(formFieldDefinitionService.listActiveByBizType(any())).thenReturn(List.of());
        lenient().when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.empty());
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
        lenient().when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());
    }

    /**
     * 创建用户时，若编号在未删除的用户中已存在，应抛出业务异常且不执行插入。
     */
    @Test
    void create_shouldThrowBusinessException_whenCodeAlreadyExists() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        UserCreateRequest request = buildCreateRequest("张三", "U001", null);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("U001");
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    /**
     * 创建用户时，若身份证号非空且在未删除的用户中已存在，应抛出业务异常且不执行插入。
     * 身份证号的唯一性校验由"表单字段定义"驱动的动态校验管线执行（默认字段定义
     * {@code isUnique=true}），不再是硬编码逻辑，因此这里桩住
     * {@code formFieldDefinitionService.listActiveByBizType} 返回一条绑定
     * {@code id_card} 列且 {@code isUnique=true} 的定义，并桩住
     * {@code userMapper.countByColumnValue} 模拟命中重复。
     */
    @Test
    void create_shouldThrowBusinessException_whenIdCardAlreadyExists() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(formFieldDefinitionService.listActiveByBizType(FormFieldBizType.USER))
                .thenReturn(List.of(buildIdCardDefinition()));
        when(userMapper.countByColumnValue(eq("id_card"), eq("110101199001011234"), any())).thenReturn(1);

        UserCreateRequest request = buildCreateRequest("张三", "U001", "110101199001011234");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("110101199001011234");
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    /**
     * 更新用户时，若编号与另一个未删除用户重复（非自身），应拒绝更新。
     */
    @Test
    void update_shouldThrowBusinessException_whenCodeConflictsWithAnotherUser() {
        UserEntity entity = buildUserEntity(1L, "张三", "U001", UserStatus.ENABLED);
        when(userMapper.selectById(1L)).thenReturn(entity);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        UserUpdateRequest request = buildUpdateRequest("张三", "U002", null, List.of());

        assertThatThrownBy(() -> userService.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("U002");
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    /**
     * 更新用户时，若身份证号非空且与另一个未删除用户重复（非自身），应拒绝更新。
     * 身份证号的唯一性校验由"表单字段定义"驱动的动态校验管线执行，桩住方式同
     * {@link #create_shouldThrowBusinessException_whenIdCardAlreadyExists}。
     */
    @Test
    void update_shouldThrowBusinessException_whenIdCardConflictsWithAnotherUser() {
        UserEntity entity = buildUserEntity(1L, "张三", "U001", UserStatus.ENABLED);
        when(userMapper.selectById(1L)).thenReturn(entity);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(formFieldDefinitionService.listActiveByBizType(FormFieldBizType.USER))
                .thenReturn(List.of(buildIdCardDefinition()));
        when(userMapper.countByColumnValue(eq("id_card"), eq("110101199001011234"), eq(1L))).thenReturn(1);

        UserUpdateRequest request = buildUpdateRequest("张三", "U001", "110101199001011234", List.of());

        assertThatThrownBy(() -> userService.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("110101199001011234");
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    /**
     * 更新用户时，若 positions 中某项携带既有任职记录 id 并修改了字段，应按行更新，
     * 保留原 createBy/createTime，刷新 updateBy/updateTime，并记录一条 {@code POSITION}
     * 资源的编辑操作日志（内嵌任职子表单补齐的操作日志记录，与用户自身的编辑日志相互独立）。
     */
    @Test
    void update_shouldKeepCreateAudit_whenUpdatingExistingPosition() {
        UserEntity entity = buildUserEntity(1L, "张三", "U001", UserStatus.ENABLED);
        when(userMapper.selectById(1L)).thenReturn(entity);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        LocalDateTime originalCreateTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        UserPositionEntity existingPosition = UserPositionEntity.builder()
                .id(10L)
                .userId(1L)
                .orgId(100L)
                .positionType("primary")
                .positionAddress("旧地址")
                .showOrder(0)
                .createBy("someone")
                .createTime(originalCreateTime)
                .updateBy("someone")
                .updateTime(originalCreateTime)
                .build();
        when(userPositionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(existingPosition));

        UserPositionRequest positionRequest = new UserPositionRequest();
        positionRequest.setId(10L);
        positionRequest.setOrgId(100L);
        positionRequest.setPositionType("primary");
        positionRequest.setPositionAddress("新地址");
        positionRequest.setShowOrder(0);

        UserUpdateRequest request = buildUpdateRequest("张三", "U001", null, List.of(positionRequest));

        userService.update(1L, request);

        ArgumentCaptor<UserPositionEntity> captor = ArgumentCaptor.forClass(UserPositionEntity.class);
        verify(userPositionMapper).updateById(captor.capture());
        UserPositionEntity updated = captor.getValue();
        assertThat(updated.getPositionAddress()).isEqualTo("新地址");
        assertThat(updated.getCreateBy()).isEqualTo("someone");
        assertThat(updated.getCreateTime()).isEqualTo(originalCreateTime);
        assertThat(updated.getUpdateTime()).isNotEqualTo(originalCreateTime);
        verify(userPositionMapper, never()).insert(any(UserPositionEntity.class));
        verify(userPositionMapper, never()).deleteByIds(anyList());
        verify(operationLogRecorder).recordUpdate(org.mockito.ArgumentMatchers.eq("user"), any(), any(),
                any(Map.class), any(Map.class));
        verify(operationLogRecorder).recordUpdate(eq("position"), eq(10L), any(), any(Map.class), any(Map.class));
    }

    /**
     * 更新用户时，若 positions 中包含一项未携带 id 的新记录，应作为新任职记录插入，
     * 拥有全新的创建审计信息，并记录一条 {@code POSITION} 资源的新增操作日志。
     */
    @Test
    void update_shouldInsertNewPosition_whenRequestItemHasNoId() {
        UserEntity entity = buildUserEntity(1L, "张三", "U001", UserStatus.ENABLED);
        when(userMapper.selectById(1L)).thenReturn(entity);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userPositionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        UserPositionRequest positionRequest = new UserPositionRequest();
        positionRequest.setOrgId(100L);
        positionRequest.setPositionType("part_time");
        positionRequest.setShowOrder(0);

        UserUpdateRequest request = buildUpdateRequest("张三", "U001", null, List.of(positionRequest));

        userService.update(1L, request);

        ArgumentCaptor<UserPositionEntity> captor = ArgumentCaptor.forClass(UserPositionEntity.class);
        verify(userPositionMapper).insert(captor.capture());
        UserPositionEntity inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(1L);
        assertThat(inserted.getOrgId()).isEqualTo(100L);
        assertThat(inserted.getCreateBy()).isEqualTo(String.valueOf(1L));
        assertThat(inserted.getCreateTime()).isNotNull();
        verify(userPositionMapper, never()).deleteByIds(anyList());
        verify(operationLogRecorder).recordCreate(eq("position"), any(), any(), any(Map.class));
    }

    /**
     * 更新用户时，若某条既有任职记录的 id 未出现在本次 positions 列表中，应被物理删除，
     * 其余既有记录不受影响，且应在物理删除前记录一条 {@code POSITION} 资源的删除操作日志。
     */
    @Test
    void update_shouldPhysicallyDeleteMissingPosition() {
        UserEntity entity = buildUserEntity(1L, "张三", "U001", UserStatus.ENABLED);
        when(userMapper.selectById(1L)).thenReturn(entity);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        UserPositionEntity kept = UserPositionEntity.builder()
                .id(10L).userId(1L).orgId(100L).positionType("primary").showOrder(0)
                .createBy("admin").createTime(LocalDateTime.now())
                .updateBy("admin").updateTime(LocalDateTime.now())
                .build();
        UserPositionEntity removed = UserPositionEntity.builder()
                .id(11L).userId(1L).orgId(101L).positionType("part_time").showOrder(0)
                .createBy("admin").createTime(LocalDateTime.now())
                .updateBy("admin").updateTime(LocalDateTime.now())
                .build();
        when(userPositionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(kept, removed));

        UserPositionRequest keptRequest = new UserPositionRequest();
        keptRequest.setId(10L);
        keptRequest.setOrgId(100L);
        keptRequest.setPositionType("primary");
        keptRequest.setShowOrder(0);

        UserUpdateRequest request = buildUpdateRequest("张三", "U001", null, List.of(keptRequest));

        userService.update(1L, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(userPositionMapper).deleteByIds(captor.capture());
        assertThat(captor.getValue()).containsExactly(11L);
        verify(userPositionMapper, never()).insert(any(UserPositionEntity.class));
        verify(operationLogRecorder).recordDelete(eq("position"), eq(11L), any(), any(Map.class));
    }

    /**
     * 更新用户时，若 positions 传空数组且该用户此前存在任职记录，应删除全部既有任职记录，
     * 并为每条被删除的任职记录各自记录一条 {@code POSITION} 资源的删除操作日志。
     */
    @Test
    void update_shouldDeleteAllPositions_whenPositionsIsEmpty() {
        UserEntity entity = buildUserEntity(1L, "张三", "U001", UserStatus.ENABLED);
        when(userMapper.selectById(1L)).thenReturn(entity);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        UserPositionEntity existing = UserPositionEntity.builder()
                .id(10L).userId(1L).orgId(100L).positionType("primary").showOrder(0)
                .createBy("admin").createTime(LocalDateTime.now())
                .updateBy("admin").updateTime(LocalDateTime.now())
                .build();
        when(userPositionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(existing));

        UserUpdateRequest request = buildUpdateRequest("张三", "U001", null, List.of());

        userService.update(1L, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(userPositionMapper).deleteByIds(captor.capture());
        assertThat(captor.getValue()).containsExactly(10L);
        verify(operationLogRecorder).recordDelete(eq("position"), eq(10L), any(), any(Map.class));
    }

    /**
     * 更新用户时，若 positions 中某项携带的 id 属于另一个用户的任职记录，应拒绝整个更新，
     * 且不产生任何任职记录写操作。
     */
    @Test
    void update_shouldThrowBusinessException_whenPositionIdBelongsToAnotherUser() {
        UserEntity entity = buildUserEntity(1L, "张三", "U001", UserStatus.ENABLED);
        when(userMapper.selectById(1L)).thenReturn(entity);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        UserPositionEntity ownPosition = UserPositionEntity.builder()
                .id(10L).userId(1L).orgId(100L).positionType("primary").showOrder(0)
                .createBy("admin").createTime(LocalDateTime.now())
                .updateBy("admin").updateTime(LocalDateTime.now())
                .build();
        when(userPositionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(ownPosition));

        UserPositionRequest foreignRequest = new UserPositionRequest();
        foreignRequest.setId(99L);
        foreignRequest.setOrgId(100L);
        foreignRequest.setPositionType("primary");
        foreignRequest.setShowOrder(0);

        UserUpdateRequest request = buildUpdateRequest("张三", "U001", null, List.of(foreignRequest));

        assertThatThrownBy(() -> userService.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("99");
        verify(userPositionMapper, never()).insert(any(UserPositionEntity.class));
        verify(userPositionMapper, never()).updateById(any(UserPositionEntity.class));
        verify(userPositionMapper, never()).deleteByIds(anyList());
        verify(operationLogRecorder, never()).recordCreate(eq("position"), any(), any(), any(Map.class));
        verify(operationLogRecorder, never()).recordUpdate(eq("position"), any(), any(), any(Map.class), any(Map.class));
        verify(operationLogRecorder, never()).recordDelete(eq("position"), any(), any(), any(Map.class));
    }

    /**
     * 查询一个不存在（或已被逻辑删除）的用户时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenUserNotFound() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 分页查询组合姓名与手机号条件时（同时提供两个可选搜索参数），应正常执行查询并
     * 返回按分页元信息组装的结果，不因组合条件而出错；跨表的组合条件、模糊搜索均已
     * 下沉到 {@code UserMapper.xml} 的 SQL 里，本用例只验证 service 层的参数透传与
     * 分页结果组装（user-org-scope-data-permission change design.md Decision 4）。
     */
    @Test
    void getPage_shouldReturnPageResult_whenCombiningNameAndMobileConditions() {
        UserVO matched = buildUserVO(1L, "张三", "U001", UserStatus.ENABLED);
        Page<UserVO> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(matched));
        when(userMapper.selectUserPage(any(), eq("张"), eq("138"), isNull(), isNull(), eq(UserStatus.DELETED),
                eq(PositionStatus.DELETED))).thenReturn(resultPage);

        var pageResult = userService.getPage("张", "138", null, 1, 10);

        assertThat(pageResult.getTotal()).isEqualTo(1L);
        assertThat(pageResult.getRecords()).hasSize(1);
        assertThat(pageResult.getRecords().get(0).getName()).isEqualTo("张三");
        verify(userMapper).selectUserPage(any(), eq("张"), eq("138"), isNull(), isNull(), eq(UserStatus.DELETED),
                eq(PositionStatus.DELETED));
    }

    /**
     * 未配置管辖组织范围（{@link OrgScopeService#resolveAllowedOrgIds} 返回空 {@link Optional}）
     * 时，应传 {@code null} 给 {@code allowedOrgIds} 参数，行为与改动前一致，不受限
     * （user-org-scope-data-permission change design.md Decision 1）。
     */
    @Test
    void getPage_shouldPassNullAllowedOrgIds_whenNotRestricted() {
        Page<UserVO> resultPage = new Page<>(1, 10, 0L);
        resultPage.setRecords(List.of());
        when(userMapper.selectUserPage(any(), any(), any(), any(), isNull(), eq(UserStatus.DELETED),
                eq(PositionStatus.DELETED))).thenReturn(resultPage);

        userService.getPage(null, null, null, 1, 10);

        verify(userMapper).selectUserPage(any(), any(), any(), any(), isNull(), eq(UserStatus.DELETED),
                eq(PositionStatus.DELETED));
    }

    /**
     * 配置了管辖组织范围时，应把解析出的允许组织 id 集合透传给 {@code selectUserPage}，
     * 由 XML 里的 {@code EXISTS} 子查询按"任一任职落在范围内即可见"的语义过滤
     * （user-org-scope-data-permission change design.md Decision 2）。
     */
    @Test
    void getPage_shouldPassAllowedOrgIds_whenScopeRestricted() {
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(100L, 200L)));
        UserVO matched = buildUserVO(1L, "张三", "U001", UserStatus.ENABLED);
        Page<UserVO> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(matched));
        when(userMapper.selectUserPage(any(), any(), any(), any(), eq(Set.of(100L, 200L)), eq(UserStatus.DELETED),
                eq(PositionStatus.DELETED))).thenReturn(resultPage);

        var pageResult = userService.getPage(null, null, null, 1, 10);

        assertThat(pageResult.getTotal()).isEqualTo(1L);
        assertThat(pageResult.getRecords()).hasSize(1);
        verify(userMapper).selectUserPage(any(), any(), any(), any(), eq(Set.of(100L, 200L)), eq(UserStatus.DELETED),
                eq(PositionStatus.DELETED));
    }

    /**
     * 创建用户成功后，应联动调用 {@link PasswordService#createDefaultPassword} 为新用户
     * 创建默认密码记录（user-management spec.md "创建用户联动生成默认密码" Scenario）。
     */
    @Test
    void create_shouldCreateDefaultPassword_whenUserCreatedSuccessfully() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(userMapper).insert(any(UserEntity.class));
        when(userMapper.selectById(100L)).thenReturn(buildUserEntity(100L, "张三", "U001", UserStatus.ENABLED));
        when(userPositionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        UserCreateRequest request = buildCreateRequest("张三", "U001", null);

        userService.create(request);

        verify(passwordService).createDefaultPassword(100L);
    }

    /**
     * 重置密码时，若用户不存在（或已被逻辑删除），应抛出业务异常且不调用密码重置逻辑。
     */
    @Test
    void resetPassword_shouldThrowBusinessException_whenUserNotFound() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.resetPassword(99L)).isInstanceOf(BusinessException.class);
        verify(passwordService, never()).resetToDefault(any());
    }

    /**
     * 重置密码时，若用户存在，应调用 {@link PasswordService#resetToDefault} 完成重置，
     * 不修改用户自身的 {@code status}（user-management spec.md "重置存在的用户密码" Scenario）。
     */
    @Test
    void resetPassword_shouldCallPasswordServiceResetToDefault_whenUserExists() {
        UserEntity entity = buildUserEntity(1L, "张三", "U001", UserStatus.ENABLED);
        when(userMapper.selectById(1L)).thenReturn(entity);

        userService.resetPassword(1L);

        verify(passwordService).resetToDefault(1L);
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    /**
     * 构造创建用户请求。
     *
     * @param name   用户姓名
     * @param code   用户编号
     * @param idCard 身份证号，可为空
     * @return 创建请求
     */
    private UserCreateRequest buildCreateRequest(String name, String code, String idCard) {
        UserCreateRequest request = new UserCreateRequest();
        request.setName(name);
        request.setCode(code);
        request.setIdCard(idCard);
        request.setShowOrder(0);
        return request;
    }

    /**
     * 构造更新用户请求。
     *
     * @param name      用户姓名
     * @param code      用户编号
     * @param idCard    身份证号，可为空
     * @param positions 任职记录请求列表
     * @return 更新请求
     */
    private UserUpdateRequest buildUpdateRequest(String name, String code, String idCard,
            List<UserPositionRequest> positions) {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setName(name);
        request.setCode(code);
        request.setIdCard(idCard);
        request.setShowOrder(0);
        request.setPositions(new ArrayList<>(positions));
        return request;
    }

    /**
     * 构造一条绑定 {@code id_card} 列、{@code isUnique=true} 的非锁定字段定义，
     * 模拟身份证号的默认表单字段定义（迁移种子数据 V21 的行为），供动态校验管线测试
     * 复用。
     *
     * @return 身份证号字段定义
     */
    private FormFieldDefinitionVO buildIdCardDefinition() {
        return FormFieldDefinitionVO.builder()
                .fieldCode("idCard")
                .fieldName("身份证号")
                .columnName("id_card")
                .controlType(FormFieldControlType.TEXT)
                .isRequired(false)
                .isUnique(true)
                .showInList(true)
                .showInCreate(true)
                .showInEdit(true)
                .editable(true)
                .locked(false)
                .build();
    }

    /**
     * 构造一个测试用的用户实体。
     *
     * @param id     主键 id
     * @param name   用户姓名
     * @param code   用户编号
     * @param status 状态
     * @return 用户实体
     */
    private UserEntity buildUserEntity(long id, String name, String code, int status) {
        return UserEntity.builder()
                .id(id)
                .name(name)
                .code(code)
                .status(status)
                .showOrder(0)
                .build();
    }

    /**
     * 构造一个测试用的用户视图对象，模拟 {@code selectUserPage} 返回的分页记录。
     *
     * @param id     主键 id
     * @param name   用户姓名
     * @param code   用户编号
     * @param status 状态
     * @return 用户视图对象
     */
    private UserVO buildUserVO(long id, String name, String code, int status) {
        return UserVO.builder()
                .id(id)
                .name(name)
                .code(code)
                .status(status)
                .showOrder(0)
                .build();
    }
}
