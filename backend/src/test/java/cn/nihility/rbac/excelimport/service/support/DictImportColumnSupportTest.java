package cn.nihility.rbac.excelimport.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.service.DictItemService;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import cn.nihility.rbac.formfield.mapper.FormFieldDefinitionMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DictImportColumnSupport} 的单元测试，重点覆盖：只识别关联了
 * {@code controlType=DICT} 表单字段定义的列（非字典列、多选字典列均被过滤掉）、
 * label 列表按 {@link DictItemService#getEnabledOptions} 返回顺序透传、label→code
 * 精确匹配反查命中/未命中两种结果。
 */
@ExtendWith(MockitoExtension.class)
class DictImportColumnSupportTest {

    /** 被测组件的表单字段定义数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private FormFieldDefinitionMapper formFieldDefinitionMapper;

    /** 被测组件的字典项业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private DictItemService dictItemService;

    /** 被测组件实例。 */
    private DictImportColumnSupport dictImportColumnSupport;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        dictImportColumnSupport = new DictImportColumnSupport(formFieldDefinitionMapper, dictItemService);
    }

    /**
     * 未关联任何 {@code formFieldDefinitionId} 的配置列表，应直接返回空 Map，不查询
     * {@code FormFieldDefinitionMapper}。
     */
    @Test
    void resolveDictColumns_shouldReturnEmpty_whenNoFormFieldDefinitionId() {
        List<ImportFieldConfigVO> configs = List.of(buildConfig("code", null));

        Map<String, String> dictColumns = dictImportColumnSupport.resolveDictColumns(configs);

        assertThat(dictColumns).isEmpty();
    }

    /**
     * 只应识别 {@code controlType=DICT} 的列，{@code TEXT}/{@code MULTI_DICT}
     * 等其他控件类型的列不应出现在返回结果中。
     */
    @Test
    void resolveDictColumns_shouldOnlyIncludeDictControlType() {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("gender", 1L),
                buildConfig("remark", 2L),
                buildConfig("tags", 3L));
        when(formFieldDefinitionMapper.selectByIds(anyList())).thenReturn(List.of(
                buildDefinition(1L, FormFieldControlType.DICT, "GENDER"),
                buildDefinition(2L, FormFieldControlType.TEXT, null),
                buildDefinition(3L, FormFieldControlType.MULTI_DICT, "TAG")));

        Map<String, String> dictColumns = dictImportColumnSupport.resolveDictColumns(configs);

        assertThat(dictColumns).containsExactly(Map.entry("gender", "GENDER"));
    }

    /**
     * label 列表应原样透传 {@link DictItemService#getEnabledOptions} 返回的顺序，
     * 无启用项时返回空列表。
     */
    @Test
    void getEnabledLabels_shouldReturnLabelsInOrder() {
        when(dictItemService.getEnabledOptions("GENDER")).thenReturn(List.of(
                DictItemOptionVO.builder().code("M").label("男").showOrder(20).build(),
                DictItemOptionVO.builder().code("F").label("女").showOrder(10).build()));

        List<String> labels = dictImportColumnSupport.getEnabledLabels("GENDER");

        assertThat(labels).containsExactly("男", "女");
    }

    /**
     * 字典类型当前没有任何启用项时，label 列表应为空列表。
     */
    @Test
    void getEnabledLabels_shouldReturnEmpty_whenNoEnabledOptions() {
        when(dictItemService.getEnabledOptions("GENDER")).thenReturn(List.of());

        List<String> labels = dictImportColumnSupport.getEnabledLabels("GENDER");

        assertThat(labels).isEmpty();
    }

    /**
     * 未关联表单字段定义（{@code formFieldDefinitionId == null}）的固定标识列应恒被判定
     * 为文本格式，不查询 {@code FormFieldDefinitionMapper}。
     */
    @Test
    void resolveTextFormatFieldCodes_shouldIncludeFixedColumn_whenNoFormFieldDefinitionId() {
        List<ImportFieldConfigVO> configs = List.of(buildConfig("__userCode", null));

        Set<String> textFormatFieldCodes = dictImportColumnSupport.resolveTextFormatFieldCodes(configs);

        assertThat(textFormatFieldCodes).containsExactly("__userCode");
    }

    /**
     * 关联了表单字段定义的列中，只有控件类型为 {@code NUMBER} 的列不应被判定为文本格式，
     * 其余控件类型（{@code TEXT}/{@code DICT}/{@code DATE}/{@code MULTI_DICT}）均应判定为
     * 文本格式。
     */
    @Test
    void resolveTextFormatFieldCodes_shouldExcludeOnlyNumberControlType() {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("remark", 1L),
                buildConfig("gender", 2L),
                buildConfig("birthday", 3L),
                buildConfig("tags", 4L),
                buildConfig("showOrder", 5L));
        when(formFieldDefinitionMapper.selectByIds(anyList())).thenReturn(List.of(
                buildDefinition(1L, FormFieldControlType.TEXT, null),
                buildDefinition(2L, FormFieldControlType.DICT, "GENDER"),
                buildDefinition(3L, FormFieldControlType.DATE, null),
                buildDefinition(4L, FormFieldControlType.MULTI_DICT, "TAG"),
                buildDefinition(5L, FormFieldControlType.NUMBER, null)));

        Set<String> textFormatFieldCodes = dictImportColumnSupport.resolveTextFormatFieldCodes(configs);

        assertThat(textFormatFieldCodes).containsExactly("remark", "gender", "birthday", "tags");
    }

    /**
     * label 精确匹配到某个启用字典项时，应返回其 code。
     */
    @Test
    void resolveCodeByLabel_shouldReturnCode_whenLabelMatched() {
        when(dictItemService.getEnabledOptions("GENDER")).thenReturn(List.of(
                DictItemOptionVO.builder().code("M").label("男").showOrder(20).build(),
                DictItemOptionVO.builder().code("F").label("女").showOrder(10).build()));

        String code = dictImportColumnSupport.resolveCodeByLabel("GENDER", "女");

        assertThat(code).isEqualTo("F");
    }

    /**
     * label 未匹配到任何启用字典项时，应返回 {@code null}，不做大小写/模糊匹配。
     */
    @Test
    void resolveCodeByLabel_shouldReturnNull_whenLabelNotMatched() {
        when(dictItemService.getEnabledOptions("GENDER")).thenReturn(List.of(
                DictItemOptionVO.builder().code("M").label("男").showOrder(20).build()));

        String code = dictImportColumnSupport.resolveCodeByLabel("GENDER", "女");

        assertThat(code).isNull();
    }

    /**
     * 构造一条测试用的导入字段配置视图对象。
     *
     * @param fieldCode              字段标识
     * @param formFieldDefinitionId  关联的表单字段定义 id，可为 {@code null}
     * @return 导入字段配置视图对象
     */
    private ImportFieldConfigVO buildConfig(String fieldCode, Long formFieldDefinitionId) {
        return ImportFieldConfigVO.builder()
                .fieldCode(fieldCode)
                .formFieldDefinitionId(formFieldDefinitionId)
                .excelHeaderName(fieldCode)
                .build();
    }

    /**
     * 构造一条测试用的表单字段定义实体。
     *
     * @param id            主键 id
     * @param controlType   控件类型
     * @param dictTypeCode  关联的字典类型编码，可为 {@code null}
     * @return 表单字段定义实体
     */
    private FormFieldDefinitionEntity buildDefinition(Long id, int controlType, String dictTypeCode) {
        return FormFieldDefinitionEntity.builder()
                .id(id)
                .controlType(controlType)
                .dictTypeCode(dictTypeCode)
                .build();
    }
}
