package cn.nihility.rbac.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.dto.AppUpdateRequest;
import cn.nihility.rbac.app.dto.AppVO;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.formfield.support.FormFieldSnapshotSupport;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppServiceImpl} 的单元测试，重点覆盖分页查询、新增默认启用、更新不改状态、
 * 启停用、逻辑删除、操作日志记录调用等分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class AppServiceImplTest {

    /** 被测服务的应用数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppMapper appMapper;

    /** 被测服务的用户数据访问依赖，用于回填负责人姓名，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /** 被测服务的组织数据访问依赖，用于回填所属组织名称，使用 Mockito 打桩。 */
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

    /** 被测服务的管辖组织范围解析依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgScopeService orgScopeService;

    /** 被测服务的当前登录操作人账号编码解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务实例。 */
    private AppServiceImpl appService;

    /**
     * MyBatis-Plus 的 {@code LambdaQueryWrapper} 需要先有实体的 {@code TableInfo} 缓存才能在
     * 脱离 Spring 容器的纯单元测试里调用 {@code getSqlSegment()} 做条件断言（正常启动时该
     * 缓存由 Spring Boot 扫描 Mapper 时自动建立），这里手动触发一次初始化，仅供本测试类使用。
     */
    @BeforeAll
    static void primeLambdaColumnCache() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "appServiceImplTest");
        assistant.setCurrentNamespace(AppEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, AppEntity.class);
    }

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过 {@code AppConvert.INSTANCE}
     * 静态调用完成，无需在此注入或 mock。动态字段定义默认桩为空列表，各分支逻辑测试
     * 不受"表单字段定义"驱动的校验管线影响。管辖组织范围默认桩为 {@code Optional.empty()}
     * （不受限制），受限场景在下方单独的用例中覆盖。
     */
    @BeforeEach
    void setUp() {
        appService = new AppServiceImpl(appMapper, userMapper, orgMapper, operationLogRecorder,
                formFieldDefinitionService, formFieldSnapshotSupport, orgScopeService, currentOperatorService);
        lenient().when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        lenient().when(orgMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        lenient().when(formFieldDefinitionService.listActiveByBizType(any())).thenReturn(List.of());
        lenient().when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.empty());
        lenient().when(currentOperatorService.resolveCode()).thenReturn("test-operator");
    }

    /**
     * 分页查询时，应返回携带总条数、页码、每页条数的分页结果。
     */
    @Test
    void getPage_shouldReturnPageResult() {
        AppEntity entity = buildEntity(10L, 1L, 100L, AppStatus.ENABLED);
        Page<AppEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(entity));
        when(appMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);

        PageResult<AppVO> pageResult = appService.getPage(1, 10);

        assertThat(pageResult.getTotal()).isEqualTo(1L);
        assertThat(pageResult.getPage()).isEqualTo(1);
        assertThat(pageResult.getPageSize()).isEqualTo(10);
        assertThat(pageResult.getRecords()).hasSize(1);
        assertThat(pageResult.getRecords().get(0).getId()).isEqualTo(10L);
    }

    /**
     * 分页查询受限时，应追加 org_id IN (:allowedOrgIds) 过滤条件
     * （org-scope-data-permission change design.md Decision 6）。
     */
    @Test
    void getPage_shouldAppendOrgIdInCondition_whenScopeRestricted() {
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(100L, 200L)));
        Page<AppEntity> resultPage = new Page<>(1, 10, 0L);
        resultPage.setRecords(List.of());
        when(appMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);

        appService.getPage(1, 10);

        ArgumentCaptor<LambdaQueryWrapper<AppEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(appMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("orgId IN");
    }

    /**
     * 分页查询受限且管辖范围解析结果意外为空集合时，应改用恒不匹配的哨兵条件，
     * 不直接把空集合传给 {@code .in(...)}（design.md Decision 6 防御性写法）。
     */
    @Test
    void getPage_shouldUseSentinelCondition_whenAllowedOrgIdsUnexpectedlyEmpty() {
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of()));
        Page<AppEntity> resultPage = new Page<>(1, 10, 0L);
        resultPage.setRecords(List.of());
        when(appMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);

        appService.getPage(1, 10);

        ArgumentCaptor<LambdaQueryWrapper<AppEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(appMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("id = ").doesNotContain("orgId IN");
    }

    /**
     * 创建应用时，应显式将状态置为启用，并写入创建/更新审计信息。
     */
    @Test
    void create_shouldSetEnabledStatus_andAuditFields() {
        AppEntity inserted = buildEntity(10L, 1L, 100L, AppStatus.ENABLED);
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(appMapper.selectById(any())).thenReturn(inserted);

        AppCreateRequest request = new AppCreateRequest();
        request.setName("测试应用");
        request.setCode("app001");
        request.setOwnerId(1L);
        request.setOrgId(100L);
        request.setShowOrder(0);

        appService.create(request);

        ArgumentCaptor<AppEntity> captor = ArgumentCaptor.forClass(AppEntity.class);
        verify(appMapper).insert(captor.capture());
        AppEntity captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(AppStatus.ENABLED);
        assertThat(captured.getCode()).isEqualTo("app001");
        assertThat(captured.getOwnerId()).isEqualTo(1L);
        assertThat(captured.getOrgId()).isEqualTo(100L);
        assertThat(captured.getCreateBy()).isEqualTo("test-operator");
        assertThat(captured.getCreateTime()).isNotNull();
        verify(operationLogRecorder).recordCreate(org.mockito.ArgumentMatchers.eq("app"), any(),
                org.mockito.ArgumentMatchers.eq("测试应用"), any(Map.class));
    }

    /**
     * 创建应用时，若应用编码已被其他未删除应用占用，应拒绝创建。
     */
    @Test
    void create_shouldThrowBusinessException_whenCodeAlreadyExists() {
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        AppCreateRequest request = new AppCreateRequest();
        request.setName("测试应用");
        request.setCode("app001");
        request.setOwnerId(1L);
        request.setOrgId(100L);
        request.setShowOrder(0);

        assertThatThrownBy(() -> appService.create(request))
                .isInstanceOf(BusinessException.class);
        verify(appMapper, never()).insert(any(AppEntity.class));
    }

    /**
     * 更新应用时，不应修改状态字段，仅更新允许修改的字段。
     */
    @Test
    void update_shouldNotChangeStatus() {
        AppEntity entity = buildEntity(10L, 1L, 100L, AppStatus.ENABLED);
        entity.setName("旧名称");
        when(appMapper.selectById(10L)).thenReturn(entity);
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        AppUpdateRequest request = new AppUpdateRequest();
        request.setName("新名称");
        request.setCode("app002");
        request.setOwnerId(2L);
        request.setOrgId(200L);
        request.setShowOrder(1);

        appService.update(10L, request);

        assertThat(entity.getStatus()).isEqualTo(AppStatus.ENABLED);
        assertThat(entity.getName()).isEqualTo("新名称");
        assertThat(entity.getCode()).isEqualTo("app002");
        assertThat(entity.getOwnerId()).isEqualTo(2L);
        assertThat(entity.getOrgId()).isEqualTo(200L);
        verify(appMapper).updateById(entity);
    }

    /**
     * 更新应用时，若应用编码已被其他未删除应用占用，应拒绝更新。
     */
    @Test
    void update_shouldThrowBusinessException_whenCodeUsedByAnotherApp() {
        AppEntity entity = buildEntity(10L, 1L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(entity);
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        AppUpdateRequest request = new AppUpdateRequest();
        request.setName("新名称");
        request.setCode("app002");
        request.setOwnerId(2L);
        request.setOrgId(200L);
        request.setShowOrder(1);

        assertThatThrownBy(() -> appService.update(10L, request))
                .isInstanceOf(BusinessException.class);
        verify(appMapper, never()).updateById(any(AppEntity.class));
    }

    /**
     * 启用应用时，应将状态置为启用。
     */
    @Test
    void enable_shouldSetEnabledStatus() {
        AppEntity entity = buildEntity(10L, 1L, 100L, AppStatus.DISABLED);
        when(appMapper.selectById(10L)).thenReturn(entity);

        appService.enable(10L);

        assertThat(entity.getStatus()).isEqualTo(AppStatus.ENABLED);
        verify(appMapper).updateById(entity);
    }

    /**
     * 停用应用时，应将状态置为停用。
     */
    @Test
    void disable_shouldSetDisabledStatus() {
        AppEntity entity = buildEntity(10L, 1L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(entity);

        appService.disable(10L);

        assertThat(entity.getStatus()).isEqualTo(AppStatus.DISABLED);
        verify(appMapper).updateById(entity);
    }

    /**
     * 删除应用时，应做逻辑删除（状态置为已删除），不做物理删除。
     */
    @Test
    void delete_shouldSetDeletedStatus() {
        AppEntity entity = buildEntity(10L, 1L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(entity);

        appService.delete(10L);

        assertThat(entity.getStatus()).isEqualTo(AppStatus.DELETED);
        verify(appMapper).updateById(entity);
        verify(appMapper, never()).deleteById(any(Long.class));
        verify(operationLogRecorder).recordDelete(org.mockito.ArgumentMatchers.eq("app"),
                org.mockito.ArgumentMatchers.eq(10L), any(), any(Map.class));
    }

    /**
     * 查询一个不存在的应用时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenAppNotFound() {
        when(appMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> appService.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 查询一个已被逻辑删除的应用时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenAppAlreadyDeleted() {
        AppEntity entity = buildEntity(10L, 1L, 100L, AppStatus.DELETED);
        when(appMapper.selectById(10L)).thenReturn(entity);

        assertThatThrownBy(() -> appService.getById(10L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 构造一个测试用的应用实体。
     *
     * @param id      主键 id
     * @param ownerId 负责人用户 id
     * @param orgId   所属组织 id
     * @param status  状态
     * @return 应用实体
     */
    private AppEntity buildEntity(long id, long ownerId, long orgId, int status) {
        LocalDateTime now = LocalDateTime.now();
        return AppEntity.builder()
                .id(id)
                .name("测试应用")
                .code("app000")
                .ownerId(ownerId)
                .orgId(orgId)
                .showOrder(0)
                .status(status)
                .createBy("admin")
                .createTime(now)
                .updateBy("admin")
                .updateTime(now)
                .build();
    }
}
