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
import cn.nihility.rbac.dict.dto.DictItemCreateRequest;
import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.dto.DictItemUpdateRequest;
import cn.nihility.rbac.dict.entity.DictItemEntity;
import cn.nihility.rbac.dict.entity.DictTypeEntity;
import cn.nihility.rbac.dict.mapper.DictItemMapper;
import cn.nihility.rbac.dict.mapper.DictTypeMapper;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DictItemServiceImpl} 的单元测试，重点覆盖编码唯一性校验范围、
 * 按字典类型编码查询启用项的降级行为、操作日志记录调用等分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class DictItemServiceImplTest {

    /** 被测服务的字典项数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private DictItemMapper dictItemMapper;

    /** 被测服务的字典类型数据访问依赖，用于回填名称及按编码解析字典类型，使用 Mockito 打桩。 */
    @Mock
    private DictTypeMapper dictTypeMapper;

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
    private DictItemServiceImpl dictItemService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过 {@code DictConvert.INSTANCE}
     * 静态调用完成，无需在此注入或 mock。
     */
    @BeforeEach
    void setUp() {
        dictItemService = new DictItemServiceImpl(dictItemMapper, dictTypeMapper, operationLogRecorder,
                currentOperatorService, userDisplayService);
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
        lenient().when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());
    }

    /**
     * 创建字典项时，若编码在同一字典类型下未删除的字典项中已存在，应抛出业务异常且不执行插入。
     */
    @Test
    void create_shouldThrowBusinessException_whenCodeAlreadyExistsInSameDictType() {
        when(dictItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        DictItemCreateRequest request = new DictItemCreateRequest();
        request.setDictTypeId(1L);
        request.setLabel("主职");
        request.setCode("primary");
        request.setShowOrder(0);

        assertThatThrownBy(() -> dictItemService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("primary");
    }

    /**
     * 创建字典项时，若相同编码存在于另一个字典类型下（唯一性校验只在同一字典类型范围内生效），
     * 应允许创建成功。
     */
    @Test
    void create_shouldSucceed_whenSameCodeExistsUnderDifferentDictType() {
        when(dictItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        DictItemEntity saved = buildEntity(10L, 2L, "主职", "primary", DictStatus.ENABLED, 0);
        when(dictItemMapper.selectById(any())).thenReturn(saved);
        when(dictTypeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        DictItemCreateRequest request = new DictItemCreateRequest();
        request.setDictTypeId(2L);
        request.setLabel("主职");
        request.setCode("primary");
        request.setShowOrder(0);

        dictItemService.create(request);

        verify(operationLogRecorder).recordCreate(org.mockito.ArgumentMatchers.eq("dictItem"), any(),
                org.mockito.ArgumentMatchers.eq("主职"), any(Map.class));
    }

    /**
     * 更新字典项时，若编码与同一字典类型下另一个未删除字典项重复（非自身），应拒绝更新。
     */
    @Test
    void update_shouldThrowBusinessException_whenCodeConflictsWithAnotherItemInSameDictType() {
        DictItemEntity entity = buildEntity(1L, 1L, "主职", "primary", DictStatus.ENABLED, 0);
        when(dictItemMapper.selectById(1L)).thenReturn(entity);
        when(dictItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        DictItemUpdateRequest request = new DictItemUpdateRequest();
        request.setLabel("主职");
        request.setCode("part_time");
        request.setShowOrder(0);

        assertThatThrownBy(() -> dictItemService.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("part_time");
    }

    /**
     * 按字典类型编码查询启用项时，若该编码不存在启用状态的字典类型（不存在/已删除/已停用均属此类），
     * 应返回空列表而非报错。
     */
    @Test
    void getEnabledOptions_shouldReturnEmptyList_whenDictTypeNotFoundOrNotEnabled() {
        when(dictTypeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        List<DictItemOptionVO> options = dictItemService.getEnabledOptions("position_type");

        assertThat(options).isEmpty();
    }

    /**
     * 按字典类型编码查询启用项时，若字典类型存在且启用，应返回其下的启用字典项精简列表；
     * 已停用的字典项不应出现在结果中（由查询条件在数据层过滤，此处以只返回启用项的
     * mock 数据模拟该过滤效果）。
     */
    @Test
    void getEnabledOptions_shouldReturnEnabledItemsOnly() {
        DictTypeEntity dictType = DictTypeEntity.builder()
                .id(1L)
                .name("任职类型")
                .code("position_type")
                .status(DictStatus.ENABLED)
                .build();
        when(dictTypeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(dictType);

        DictItemEntity enabledItem = buildEntity(1L, 1L, "主职", "primary", DictStatus.ENABLED, 3);
        when(dictItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(enabledItem));

        List<DictItemOptionVO> options = dictItemService.getEnabledOptions("position_type");

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getCode()).isEqualTo("primary");
        assertThat(options.get(0).getLabel()).isEqualTo("主职");
    }

    /**
     * 查询一个不存在（或已被逻辑删除）的字典项时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenDictItemNotFound() {
        when(dictItemMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> dictItemService.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 构造一个测试用的字典项实体。
     *
     * @param id         主键 id
     * @param dictTypeId 所属字典类型 id
     * @param label      字典项标签
     * @param code       字典项编码
     * @param status     状态
     * @param showOrder  显示序号
     * @return 字典项实体
     */
    private DictItemEntity buildEntity(long id, long dictTypeId, String label, String code, int status,
            int showOrder) {
        return DictItemEntity.builder()
                .id(id)
                .dictTypeId(dictTypeId)
                .label(label)
                .code(code)
                .status(status)
                .showOrder(showOrder)
                .build();
    }
}
