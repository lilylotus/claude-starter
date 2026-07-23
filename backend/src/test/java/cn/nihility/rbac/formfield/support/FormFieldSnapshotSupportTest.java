package cn.nihility.rbac.formfield.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.entity.DictTypeEntity;
import cn.nihility.rbac.dict.mapper.DictTypeMapper;
import cn.nihility.rbac.dict.service.DictItemService;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FormFieldSnapshotSupport} 的单元测试，重点覆盖操作日志字段快照对
 * {@code DICT}/{@code MULTI_DICT} 类型扩展字段的编码到标签解析，以及非字典类控件、
 * 字典项已停用/找不到时的回退展示行为。
 */
@ExtendWith(MockitoExtension.class)
class FormFieldSnapshotSupportTest {

    /** 被测组件的字典类型数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private DictTypeMapper dictTypeMapper;

    /** 被测组件的字典项业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private DictItemService dictItemService;

    /** 被测组件实例。 */
    private FormFieldSnapshotSupport formFieldSnapshotSupport;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        formFieldSnapshotSupport = new FormFieldSnapshotSupport(dictTypeMapper, dictItemService);
    }

    /**
     * 非字典类控件（如 {@code TEXT}）的扩展字段应原样写入存储值，不触发字典解析。
     */
    @Test
    void appendExtFieldSnapshot_shouldKeepRawValue_whenNotDictType() {
        FormFieldDefinitionVO definition = buildDefinition("备注", "ext1", FormFieldControlType.TEXT, null);
        Map<String, Object> snapshot = new LinkedHashMap<>();

        formFieldSnapshotSupport.appendExtFieldSnapshot(snapshot, List.of(definition), Map.of("ext1", "hello"));

        assertThat(snapshot.get("备注")).isEqualTo("hello");
    }

    /**
     * {@code DICT} 类型扩展字段应把存储的单个编码解析为字典项标签。
     */
    @Test
    void appendExtFieldSnapshot_shouldResolveLabel_whenDictType() {
        FormFieldDefinitionVO definition = buildDefinition("性别", "ext2", FormFieldControlType.DICT, 1L);
        when(dictTypeMapper.selectById(1L)).thenReturn(DictTypeEntity.builder().id(1L).code("gender").build());
        when(dictItemService.getEnabledOptions("gender")).thenReturn(
                List.of(DictItemOptionVO.builder().code("M").label("男").build(),
                        DictItemOptionVO.builder().code("F").label("女").build()));
        Map<String, Object> snapshot = new LinkedHashMap<>();

        formFieldSnapshotSupport.appendExtFieldSnapshot(snapshot, List.of(definition), Map.of("ext2", "M"));

        assertThat(snapshot.get("性别")).isEqualTo("男");
    }

    /**
     * {@code DICT} 类型扩展字段存储的编码在启用字典项中找不到（如已停用/删除）时，
     * 应回退展示原始编码，而不是空白或抛出异常。
     */
    @Test
    void appendExtFieldSnapshot_shouldFallbackToRawCode_whenDictItemNotFound() {
        FormFieldDefinitionVO definition = buildDefinition("性别", "ext2", FormFieldControlType.DICT, 1L);
        when(dictTypeMapper.selectById(1L)).thenReturn(DictTypeEntity.builder().id(1L).code("gender").build());
        when(dictItemService.getEnabledOptions("gender")).thenReturn(
                List.of(DictItemOptionVO.builder().code("M").label("男").build()));
        Map<String, Object> snapshot = new LinkedHashMap<>();

        formFieldSnapshotSupport.appendExtFieldSnapshot(snapshot, List.of(definition), Map.of("ext2", "X"));

        assertThat(snapshot.get("性别")).isEqualTo("X");
    }

    /**
     * {@code MULTI_DICT} 类型扩展字段应把逗号分隔的多个存储编码逐一解析为标签，
     * 并用"、"重新拼接。
     */
    @Test
    void appendExtFieldSnapshot_shouldResolveAndJoinLabels_whenMultiDictType() {
        FormFieldDefinitionVO definition = buildDefinition("标签", "ext3", FormFieldControlType.MULTI_DICT, 2L);
        when(dictTypeMapper.selectById(2L)).thenReturn(DictTypeEntity.builder().id(2L).code("tag_type").build());
        when(dictItemService.getEnabledOptions("tag_type")).thenReturn(
                List.of(DictItemOptionVO.builder().code("A").label("标签A").build(),
                        DictItemOptionVO.builder().code("B").label("标签B").build()));
        Map<String, Object> snapshot = new LinkedHashMap<>();

        formFieldSnapshotSupport.appendExtFieldSnapshot(snapshot, List.of(definition), Map.of("ext3", "A,B"));

        assertThat(snapshot.get("标签")).isEqualTo("标签A、标签B");
    }

    /**
     * {@code MULTI_DICT} 类型扩展字段中，部分存储编码在启用字典项中找不到时，
     * 应对找不到的编码单独回退展示原始编码，其余编码仍正常解析为标签。
     */
    @Test
    void appendExtFieldSnapshot_shouldFallbackPerCode_whenSomeMultiDictItemsNotFound() {
        FormFieldDefinitionVO definition = buildDefinition("标签", "ext3", FormFieldControlType.MULTI_DICT, 2L);
        when(dictTypeMapper.selectById(2L)).thenReturn(DictTypeEntity.builder().id(2L).code("tag_type").build());
        when(dictItemService.getEnabledOptions("tag_type")).thenReturn(
                List.of(DictItemOptionVO.builder().code("A").label("标签A").build()));
        Map<String, Object> snapshot = new LinkedHashMap<>();

        formFieldSnapshotSupport.appendExtFieldSnapshot(snapshot, List.of(definition), Map.of("ext3", "A,C"));

        assertThat(snapshot.get("标签")).isEqualTo("标签A、C");
    }

    /**
     * {@code DICT}/{@code MULTI_DICT} 类型但未关联字典类型（{@code dictTypeId} 为空）时，
     * 应原样展示存储值，不触发字典解析（沿用未配置字典的既有行为）。
     */
    @Test
    void appendExtFieldSnapshot_shouldKeepRawValue_whenDictTypeIdMissing() {
        FormFieldDefinitionVO definition = buildDefinition("标签", "ext3", FormFieldControlType.MULTI_DICT, null);
        Map<String, Object> snapshot = new LinkedHashMap<>();

        formFieldSnapshotSupport.appendExtFieldSnapshot(snapshot, List.of(definition), Map.of("ext3", "A,B"));

        assertThat(snapshot.get("标签")).isEqualTo("A,B");
    }

    /**
     * 字段定义列表为空时，不应对快照做任何修改。
     */
    @Test
    void appendExtFieldSnapshot_shouldDoNothing_whenDefinitionsEmpty() {
        Map<String, Object> snapshot = new LinkedHashMap<>();

        formFieldSnapshotSupport.appendExtFieldSnapshot(snapshot, List.of(), Map.of("ext1", "hello"));

        assertThat(snapshot).isEmpty();
    }

    /**
     * 构造一条用于测试的字段定义视图对象，仅填充解析逻辑所需的字段。
     *
     * @param fieldName   展示名称
     * @param columnName  绑定的扩展列名
     * @param controlType 控件类型
     * @param dictTypeId  关联的字典类型 id
     * @return 字段定义视图对象
     */
    private FormFieldDefinitionVO buildDefinition(String fieldName, String columnName, int controlType,
            Long dictTypeId) {
        return FormFieldDefinitionVO.builder()
                .fieldName(fieldName)
                .columnName(columnName)
                .controlType(controlType)
                .dictTypeId(dictTypeId)
                .build();
    }
}
