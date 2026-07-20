package cn.nihility.rbac.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.dto.UserPositionRequest;
import cn.nihility.rbac.user.dto.UserUpdateRequest;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /** 被测服务实例。 */
    private UserServiceImpl userService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过 {@code UserConvert.INSTANCE}
     * 静态调用完成，无需在此注入或 mock。
     */
    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, userPositionMapper, orgMapper, operationLogRecorder);
        lenient().when(orgMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
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
     */
    @Test
    void create_shouldThrowBusinessException_whenIdCardAlreadyExists() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L).thenReturn(1L);

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
     */
    @Test
    void update_shouldThrowBusinessException_whenIdCardConflictsWithAnotherUser() {
        UserEntity entity = buildUserEntity(1L, "张三", "U001", UserStatus.ENABLED);
        when(userMapper.selectById(1L)).thenReturn(entity);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L).thenReturn(1L);

        UserUpdateRequest request = buildUpdateRequest("张三", "U001", "110101199001011234", List.of());

        assertThatThrownBy(() -> userService.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("110101199001011234");
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    /**
     * 更新用户时，若 positions 中某项携带既有任职记录 id 并修改了字段，应按行更新，
     * 保留原 createBy/createTime，刷新 updateBy/updateTime。
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
    }

    /**
     * 更新用户时，若 positions 中包含一项未携带 id 的新记录，应作为新任职记录插入，
     * 拥有全新的创建审计信息。
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
        assertThat(inserted.getCreateBy()).isNotNull();
        assertThat(inserted.getCreateTime()).isNotNull();
        verify(userPositionMapper, never()).deleteByIds(anyList());
    }

    /**
     * 更新用户时，若某条既有任职记录的 id 未出现在本次 positions 列表中，应被物理删除，
     * 其余既有记录不受影响。
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
    }

    /**
     * 更新用户时，若 positions 传空数组且该用户此前存在任职记录，应删除全部既有任职记录。
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
     * 返回按分页元信息组装的结果，不因组合条件而出错（具体的“与”关系由 MyBatis-Plus
     * {@code LambdaQueryWrapper} 依次叠加 {@code like} 条件的默认行为保证）。
     */
    @Test
    void getPage_shouldReturnPageResult_whenCombiningNameAndMobileConditions() {
        UserEntity matched = buildUserEntity(1L, "张三", "U001", UserStatus.ENABLED);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserEntity> resultPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(matched));
        when(userMapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(resultPage);

        var pageResult = userService.getPage("张", "138", null, 1, 10);

        assertThat(pageResult.getTotal()).isEqualTo(1L);
        assertThat(pageResult.getRecords()).hasSize(1);
        assertThat(pageResult.getRecords().get(0).getName()).isEqualTo("张三");
        verify(userMapper).selectPage(any(), any(LambdaQueryWrapper.class));
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
}
