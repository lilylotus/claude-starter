package cn.nihility.rbac.appaccess.override.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.appaccess.override.constant.OverrideType;
import cn.nihility.rbac.appaccess.override.dto.ManualOverrideUpsertRequest;
import cn.nihility.rbac.appaccess.override.dto.ManualOverrideVO;
import cn.nihility.rbac.appaccess.override.entity.ManualOverrideEntity;
import cn.nihility.rbac.appaccess.override.mapper.ManualOverrideMapper;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ManualOverrideServiceImpl} 的单元测试（tasks.md 8.3），覆盖新增
 * {@code GRANT}/{@code DENY}、重复提交同一用户应用组合触发更新而非新增、删除后退回策略
 * 判定（本类只验证删除接口本身的行为，退回策略判定由
 * {@code AppAccessEffectivePermissionServiceImpl} 覆盖）。
 */
@ExtendWith(MockitoExtension.class)
class ManualOverrideServiceImplTest {

    /** 被测服务的人工例外数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private ManualOverrideMapper manualOverrideMapper;

    /** 被测服务的当前登录操作人用户 id 解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务实例。 */
    private ManualOverrideServiceImpl manualOverrideService;

    /**
     * 每个用例执行前重新构造被测服务，并让 {@code manualOverrideMapper.insert} 模拟自增
     * 主键回填。
     */
    @BeforeEach
    void setUp() {
        manualOverrideService = new ManualOverrideServiceImpl(manualOverrideMapper, currentOperatorService);
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
        lenient().doAnswer(invocation -> {
            ManualOverrideEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        }).when(manualOverrideMapper).insert(any(ManualOverrideEntity.class));
    }

    /**
     * 用户+应用组合此前不存在人工例外时，提交 {@code GRANT} 应新增一条记录。
     */
    @Test
    void upsert_shouldInsert_whenNewGrant() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(manualOverrideMapper.selectVOById(900L)).thenReturn(
                ManualOverrideVO.builder().id(900L).userId(10L).appId(20L).overrideType(OverrideType.GRANT).build());

        ManualOverrideUpsertRequest request = buildRequest(10L, 20L, OverrideType.GRANT);
        ManualOverrideVO vo = manualOverrideService.upsert(request);

        assertThat(vo.getId()).isEqualTo(900L);
        assertThat(vo.getOverrideType()).isEqualTo(OverrideType.GRANT);
        verify(manualOverrideMapper).insert(any(ManualOverrideEntity.class));
        verify(manualOverrideMapper, never()).updateById(any(ManualOverrideEntity.class));
    }

    /**
     * 用户+应用组合此前不存在人工例外时，提交 {@code DENY} 应新增一条记录。
     */
    @Test
    void upsert_shouldInsert_whenNewDeny() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(manualOverrideMapper.selectVOById(900L)).thenReturn(
                ManualOverrideVO.builder().id(900L).userId(10L).appId(20L).overrideType(OverrideType.DENY).build());

        ManualOverrideUpsertRequest request = buildRequest(10L, 20L, OverrideType.DENY);
        ManualOverrideVO vo = manualOverrideService.upsert(request);

        assertThat(vo.getOverrideType()).isEqualTo(OverrideType.DENY);
        verify(manualOverrideMapper).insert(any(ManualOverrideEntity.class));
    }

    /**
     * 用户+应用组合已存在人工例外时，重复提交应更新已有记录的类型与备注，而不是新增一行。
     */
    @Test
    void upsert_shouldUpdate_whenAlreadyExists() {
        ManualOverrideEntity existing = ManualOverrideEntity.builder()
                .id(800L)
                .userId(10L)
                .appId(20L)
                .overrideType(OverrideType.GRANT)
                .build();
        when(manualOverrideMapper.selectOne(any())).thenReturn(existing);
        when(manualOverrideMapper.selectVOById(800L)).thenReturn(
                ManualOverrideVO.builder().id(800L).userId(10L).appId(20L).overrideType(OverrideType.DENY)
                        .remark("收回").build());

        ManualOverrideUpsertRequest request = buildRequest(10L, 20L, OverrideType.DENY);
        request.setRemark("收回");
        ManualOverrideVO vo = manualOverrideService.upsert(request);

        assertThat(vo.getId()).isEqualTo(800L);
        assertThat(vo.getOverrideType()).isEqualTo(OverrideType.DENY);
        ArgumentCaptor<ManualOverrideEntity> captor = ArgumentCaptor.forClass(ManualOverrideEntity.class);
        verify(manualOverrideMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(800L);
        assertThat(captor.getValue().getOverrideType()).isEqualTo(OverrideType.DENY);
        verify(manualOverrideMapper, never()).insert(any(ManualOverrideEntity.class));
    }

    /**
     * 非法的例外类型应拒绝保存。
     */
    @Test
    void upsert_shouldReject_whenOverrideTypeInvalid() {
        ManualOverrideUpsertRequest request = buildRequest(10L, 20L, "OTHER");

        assertThatThrownBy(() -> manualOverrideService.upsert(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法的例外类型");
        verify(manualOverrideMapper, never()).insert(any(ManualOverrideEntity.class));
    }

    /**
     * 删除存在的人工例外记录应正常执行物理删除。
     */
    @Test
    void delete_shouldRemoveRecord_whenExists() {
        when(manualOverrideMapper.selectById(800L)).thenReturn(ManualOverrideEntity.builder().id(800L).build());

        manualOverrideService.delete(800L);

        verify(manualOverrideMapper).deleteById(800L);
    }

    /**
     * 删除不存在的人工例外记录应拒绝。
     */
    @Test
    void delete_shouldThrowException_whenNotFound() {
        when(manualOverrideMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> manualOverrideService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("人工例外不存在");
        verify(manualOverrideMapper, never()).deleteById(anyLong());
    }

    /**
     * 构造人工例外新增/更新请求。
     *
     * @param userId       用户 id
     * @param appId        应用 id
     * @param overrideType 例外类型
     * @return 请求对象
     */
    private ManualOverrideUpsertRequest buildRequest(Long userId, Long appId, String overrideType) {
        ManualOverrideUpsertRequest request = new ManualOverrideUpsertRequest();
        request.setUserId(userId);
        request.setAppId(appId);
        request.setOverrideType(overrideType);
        return request;
    }
}
