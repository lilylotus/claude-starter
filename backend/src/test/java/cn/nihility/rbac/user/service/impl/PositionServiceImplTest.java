package cn.nihility.rbac.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.service.support.PositionDynamicFieldSupport;
import cn.nihility.rbac.user.service.support.PositionLogSnapshotSupport;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PositionServiceImpl} 的单元测试，重点覆盖 orgId 必填校验、分页查询（走
 * {@code UserPositionMapper.selectPositionPage} 的 XML 多表 JOIN）、新增默认启用、
 * 启停用、逻辑删除、操作日志记录调用等分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class PositionServiceImplTest {

    /** 被测服务的用户任职记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserPositionMapper userPositionMapper;

    /** 被测服务的用户数据访问依赖，用于回填所属用户姓名，使用 Mockito 打桩。 */
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

    /** 被测服务实例。 */
    private PositionServiceImpl positionService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过 {@code PositionConvert.INSTANCE}
     * 静态调用完成，无需在此注入或 mock。动态字段定义默认桩为空列表，各分支逻辑测试
     * 不受"表单字段定义"驱动的校验管线影响。{@link PositionDynamicFieldSupport} 直接用
     * 已打桩的 {@code formFieldDefinitionService}/{@code userPositionMapper} 构造真实实例，
     * 不额外 mock；{@link PositionLogSnapshotSupport} 同理，直接用已打桩的
     * {@code userMapper}/{@code orgMapper}/{@code formFieldDefinitionService} 构造真实实例。
     */
    @BeforeEach
    void setUp() {
        PositionDynamicFieldSupport positionDynamicFieldSupport =
                new PositionDynamicFieldSupport(formFieldDefinitionService, userPositionMapper);
        PositionLogSnapshotSupport positionLogSnapshotSupport =
                new PositionLogSnapshotSupport(userMapper, orgMapper, formFieldDefinitionService);
        positionService = new PositionServiceImpl(userPositionMapper, operationLogRecorder,
                positionDynamicFieldSupport, positionLogSnapshotSupport);
        lenient().when(formFieldDefinitionService.listActiveByBizType(any())).thenReturn(List.of());
    }

    /**
     * 分页查询时若未携带 orgId，应拒绝查询，不做默认聚合查询。
     */
    @Test
    void getPage_shouldThrowBusinessException_whenOrgIdIsNull() {
        assertThatThrownBy(() -> positionService.getPage(null, 1, 10))
                .isInstanceOf(BusinessException.class);
        verify(userPositionMapper, never()).selectPositionPage(any(), any(), anyInt());
    }

    /**
     * 分页查询指定 orgId 时，应返回携带总条数、页码、每页条数的分页结果，
     * 且直接使用 {@code selectPositionPage} 返回的已包含 userName/orgName 的记录，
     * 不再对用户表/组织表发起额外查询。
     */
    @Test
    void getPage_shouldReturnPageResult_whenOrgIdProvided() {
        PositionVO record = buildVO(10L, 1L, "张三", 100L, "研发部", PositionStatus.ENABLED);
        Page<PositionVO> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(record));
        when(userPositionMapper.selectPositionPage(any(IPage.class), eq(100L), eq(PositionStatus.DELETED)))
                .thenReturn(resultPage);

        PageResult<PositionVO> pageResult = positionService.getPage(100L, 1, 10);

        assertThat(pageResult.getTotal()).isEqualTo(1L);
        assertThat(pageResult.getPage()).isEqualTo(1);
        assertThat(pageResult.getPageSize()).isEqualTo(10);
        assertThat(pageResult.getRecords()).hasSize(1);
        assertThat(pageResult.getRecords().get(0).getId()).isEqualTo(10L);
        assertThat(pageResult.getRecords().get(0).getUserName()).isEqualTo("张三");
        assertThat(pageResult.getRecords().get(0).getOrgName()).isEqualTo("研发部");
    }

    /**
     * 创建任职记录时，应显式将状态置为启用，并写入创建/更新审计信息。
     */
    @Test
    void create_shouldSetEnabledStatus_andAuditFields() {
        PositionVO inserted = buildVO(10L, 1L, "张三", 100L, "研发部", PositionStatus.ENABLED);
        when(userPositionMapper.selectPositionDetail(any(), eq(PositionStatus.DELETED))).thenReturn(inserted);

        PositionCreateRequest request = new PositionCreateRequest();
        request.setUserId(1L);
        request.setOrgId(100L);
        request.setPositionType("primary");
        request.setShowOrder(0);

        positionService.create(request);

        ArgumentCaptor<UserPositionEntity> captor = ArgumentCaptor.forClass(UserPositionEntity.class);
        verify(userPositionMapper).insert(captor.capture());
        UserPositionEntity captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(PositionStatus.ENABLED);
        assertThat(captured.getUserId()).isEqualTo(1L);
        assertThat(captured.getOrgId()).isEqualTo(100L);
        assertThat(captured.getCreateBy()).isNotNull();
        assertThat(captured.getCreateTime()).isNotNull();
        verify(operationLogRecorder).recordCreate(eq("position"), any(), any(), any(Map.class));
    }

    /**
     * 更新任职记录时，不应修改所属用户及状态字段，仅更新允许修改的字段。
     */
    @Test
    void update_shouldNotChangeUserIdOrStatus() {
        UserPositionEntity entity = buildEntity(10L, 1L, 100L, PositionStatus.ENABLED);
        entity.setPositionAddress("旧地址");
        when(userPositionMapper.selectById(10L)).thenReturn(entity);
        when(userPositionMapper.selectPositionDetail(eq(10L), eq(PositionStatus.DELETED)))
                .thenReturn(buildVO(10L, 1L, "张三", 200L, "财务部", PositionStatus.ENABLED));

        PositionUpdateRequest request = new PositionUpdateRequest();
        request.setOrgId(200L);
        request.setPositionType("part_time");
        request.setPositionAddress("新地址");
        request.setShowOrder(1);

        positionService.update(10L, request);

        assertThat(entity.getUserId()).isEqualTo(1L);
        assertThat(entity.getStatus()).isEqualTo(PositionStatus.ENABLED);
        assertThat(entity.getOrgId()).isEqualTo(200L);
        assertThat(entity.getPositionAddress()).isEqualTo("新地址");
        verify(userPositionMapper).updateById(entity);
    }

    /**
     * 启用任职记录时，应将状态置为启用。
     */
    @Test
    void enable_shouldSetEnabledStatus() {
        UserPositionEntity entity = buildEntity(10L, 1L, 100L, PositionStatus.DISABLED);
        when(userPositionMapper.selectById(10L)).thenReturn(entity);
        when(userPositionMapper.selectPositionDetail(eq(10L), eq(PositionStatus.DELETED)))
                .thenReturn(buildVO(10L, 1L, "张三", 100L, "研发部", PositionStatus.ENABLED));

        positionService.enable(10L);

        assertThat(entity.getStatus()).isEqualTo(PositionStatus.ENABLED);
        verify(userPositionMapper).updateById(entity);
    }

    /**
     * 停用任职记录时，应将状态置为停用。
     */
    @Test
    void disable_shouldSetDisabledStatus() {
        UserPositionEntity entity = buildEntity(10L, 1L, 100L, PositionStatus.ENABLED);
        when(userPositionMapper.selectById(10L)).thenReturn(entity);
        when(userPositionMapper.selectPositionDetail(eq(10L), eq(PositionStatus.DELETED)))
                .thenReturn(buildVO(10L, 1L, "张三", 100L, "研发部", PositionStatus.DISABLED));

        positionService.disable(10L);

        assertThat(entity.getStatus()).isEqualTo(PositionStatus.DISABLED);
        verify(userPositionMapper).updateById(entity);
    }

    /**
     * 删除任职记录时，应做逻辑删除（状态置为已删除），不做物理删除。
     */
    @Test
    void delete_shouldSetDeletedStatus() {
        UserPositionEntity entity = buildEntity(10L, 1L, 100L, PositionStatus.ENABLED);
        when(userPositionMapper.selectById(10L)).thenReturn(entity);

        positionService.delete(10L);

        assertThat(entity.getStatus()).isEqualTo(PositionStatus.DELETED);
        verify(userPositionMapper).updateById(entity);
        verify(userPositionMapper, never()).deleteById(any(Long.class));
        verify(operationLogRecorder).recordDelete(eq("position"), eq(10L), any(), any(Map.class));
    }

    /**
     * 查询一个不存在的任职记录时，应抛出业务异常；{@code selectPositionDetail} 在
     * XML 里已经用 {@code status != DELETED} 过滤，查不到时统一返回 {@code null}。
     */
    @Test
    void getById_shouldThrowBusinessException_whenPositionNotFound() {
        when(userPositionMapper.selectPositionDetail(eq(99L), eq(PositionStatus.DELETED))).thenReturn(null);

        assertThatThrownBy(() -> positionService.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 查询一个已被逻辑删除的任职记录时，应抛出业务异常；已删除记录被 XML 里的
     * {@code status != DELETED} 条件过滤掉，{@code selectPositionDetail} 同样返回 {@code null}。
     */
    @Test
    void getById_shouldThrowBusinessException_whenPositionAlreadyDeleted() {
        when(userPositionMapper.selectPositionDetail(eq(10L), eq(PositionStatus.DELETED))).thenReturn(null);

        assertThatThrownBy(() -> positionService.getById(10L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 构造一个测试用的任职记录实体。
     *
     * @param id     主键 id
     * @param userId 所属用户 id
     * @param orgId  所属组织 id
     * @param status 状态
     * @return 任职记录实体
     */
    private UserPositionEntity buildEntity(long id, long userId, long orgId, int status) {
        LocalDateTime now = LocalDateTime.now();
        return UserPositionEntity.builder()
                .id(id)
                .userId(userId)
                .orgId(orgId)
                .positionType("primary")
                .showOrder(0)
                .status(status)
                .createBy("admin")
                .createTime(now)
                .updateBy("admin")
                .updateTime(now)
                .build();
    }

    /**
     * 构造一个测试用的任职记录视图对象，模拟 {@code selectPositionPage}/{@code selectPositionDetail}
     * 的 SQL JOIN 结果（已包含回填好的 userName/orgName）。
     *
     * @param id      主键 id
     * @param userId  所属用户 id
     * @param userName 所属用户姓名
     * @param orgId   所属组织 id
     * @param orgName 所属组织名称
     * @param status  状态
     * @return 任职记录视图对象
     */
    private PositionVO buildVO(long id, long userId, String userName, long orgId, String orgName, int status) {
        return PositionVO.builder()
                .id(id)
                .userId(userId)
                .userName(userName)
                .orgId(orgId)
                .orgName(orgName)
                .positionType("primary")
                .showOrder(0)
                .status(status)
                .build();
    }
}
