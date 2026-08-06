package cn.nihility.rbac.dict.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.dict.constant.DictStatus;
import cn.nihility.rbac.dict.dto.DictTypeCreateRequest;
import cn.nihility.rbac.dict.dto.DictTypeUpdateRequest;
import cn.nihility.rbac.dict.entity.DictTypeEntity;
import cn.nihility.rbac.dict.mapper.DictItemMapper;
import cn.nihility.rbac.dict.mapper.DictTypeMapper;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DictTypeServiceImpl} 的单元测试，重点覆盖编码唯一性校验、删除前置校验、
 * 操作日志记录调用等分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class DictTypeServiceImplTest {

    /** 被测服务的字典类型数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private DictTypeMapper dictTypeMapper;

    /** 被测服务的字典项数据访问依赖，用于删除前置校验，使用 Mockito 打桩。 */
    @Mock
    private DictItemMapper dictItemMapper;

    /** 被测服务的操作日志记录组件依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogRecorder operationLogRecorder;

    /** 被测服务的当前登录操作人用户 id 解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务的审计字段展示名批量解析依赖，使用 Mockito 打桩。 */
    @Mock
    private UserDisplayService userDisplayService;

    /** 被测服务实例。 */
    private DictTypeServiceImpl dictTypeService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过 {@code DictConvert.INSTANCE}
     * 静态调用完成，无需在此注入或 mock。
     */
    @BeforeEach
    void setUp() {
        dictTypeService = new DictTypeServiceImpl(dictTypeMapper, dictItemMapper, operationLogRecorder,
                currentOperatorService, userDisplayService);
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
        lenient().when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());
    }

    /**
     * 创建字典类型时，若编码在未删除的字典类型中已存在，应抛出业务异常且不执行插入。
     */
    @Test
    void create_shouldThrowBusinessException_whenCodeAlreadyExists() {
        when(dictTypeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        DictTypeCreateRequest request = new DictTypeCreateRequest();
        request.setName("任职类型");
        request.setCode("position_type");
        request.setShowOrder(0);

        assertThatThrownBy(() -> dictTypeService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("position_type");
    }

    /**
     * 更新字典类型时，若编码与另一个未删除字典类型重复（非自身），应拒绝更新。
     */
    @Test
    void update_shouldThrowBusinessException_whenCodeConflictsWithAnotherDictType() {
        DictTypeEntity entity = buildEntity(1L, "任职类型", "position_type", DictStatus.ENABLED, 0);
        when(dictTypeMapper.selectById(1L)).thenReturn(entity);
        when(dictTypeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        DictTypeUpdateRequest request = new DictTypeUpdateRequest();
        request.setName("任职类型");
        request.setCode("other_type");
        request.setShowOrder(0);

        assertThatThrownBy(() -> dictTypeService.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("other_type");
    }

    /**
     * 删除字典类型时，若存在未删除的字典项，应拒绝删除并给出明确提示。
     */
    @Test
    void delete_shouldThrowBusinessException_whenUndeletedItemsExist() {
        DictTypeEntity entity = buildEntity(1L, "任职类型", "position_type", DictStatus.ENABLED, 0);
        when(dictTypeMapper.selectById(1L)).thenReturn(entity);
        when(dictItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> dictTypeService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字典项");
    }

    /**
     * 删除字典类型时，若不存在未删除的字典项，应正常执行逻辑删除。
     */
    @Test
    void delete_shouldSucceed_whenNoUndeletedItemsExist() {
        DictTypeEntity entity = buildEntity(1L, "任职类型", "position_type", DictStatus.ENABLED, 0);
        when(dictTypeMapper.selectById(1L)).thenReturn(entity);
        when(dictItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        dictTypeService.delete(1L);

        assertThat(entity.getStatus()).isEqualTo(DictStatus.DELETED);
        verify(operationLogRecorder).recordDelete(org.mockito.ArgumentMatchers.eq("dictType"),
                org.mockito.ArgumentMatchers.eq(1L), any(), any(Map.class));
    }

    /**
     * 查询一个不存在（或已被逻辑删除）的字典类型时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenDictTypeNotFound() {
        when(dictTypeMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> dictTypeService.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 查询一个已被逻辑删除的字典类型时，应视为不存在并抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenDictTypeDeleted() {
        DictTypeEntity entity = buildEntity(1L, "任职类型", "position_type", DictStatus.DELETED, 0);
        when(dictTypeMapper.selectById(1L)).thenReturn(entity);

        assertThatThrownBy(() -> dictTypeService.getById(1L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 构造一个测试用的字典类型实体。
     *
     * @param id        主键 id
     * @param name      字典类型名称
     * @param code      字典类型编码
     * @param status    状态
     * @param showOrder 显示序号
     * @return 字典类型实体
     */
    private DictTypeEntity buildEntity(long id, String name, String code, int status, int showOrder) {
        return DictTypeEntity.builder()
                .id(id)
                .name(name)
                .code(code)
                .status(status)
                .showOrder(showOrder)
                .build();
    }
}
