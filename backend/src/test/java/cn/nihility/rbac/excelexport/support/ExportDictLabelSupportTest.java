package cn.nihility.rbac.excelexport.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.service.DictItemService;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ExportDictLabelSupport} 的单元测试，重点覆盖单选字典命中/未命中回退、
 * 多选字典混合命中/未命中、非字典类控件原样返回等场景（tasks.md 3.2）。
 */
@ExtendWith(MockitoExtension.class)
class ExportDictLabelSupportTest {

    /** 被测组件的字典项业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private DictItemService dictItemService;

    /** 被测组件实例。 */
    private ExportDictLabelSupport exportDictLabelSupport;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        exportDictLabelSupport = new ExportDictLabelSupport(dictItemService);
    }

    /**
     * 单选字典命中启用字典项时，应展示对应标签。
     */
    @Test
    void resolveDisplayText_shouldResolveLabel_whenSingleDictHit() {
        when(dictItemService.getEnabledOptions("gender")).thenReturn(
                List.of(DictItemOptionVO.builder().code("M").label("男").build()));

        String text = exportDictLabelSupport.resolveDisplayText(FormFieldControlType.DICT, "gender", "M");

        assertThat(text).isEqualTo("男");
    }

    /**
     * 单选字典未命中（编码在启用字典项中查不到）时，应回退展示原始编码。
     */
    @Test
    void resolveDisplayText_shouldFallbackToRawCode_whenSingleDictMiss() {
        when(dictItemService.getEnabledOptions("gender")).thenReturn(
                List.of(DictItemOptionVO.builder().code("M").label("男").build()));

        String text = exportDictLabelSupport.resolveDisplayText(FormFieldControlType.DICT, "gender", "X");

        assertThat(text).isEqualTo("X");
    }

    /**
     * 多选字典按逗号切分后逐个解析，命中的解析为标签、未命中的回退原始编码，
     * 最终用顿号"、"拼接。
     */
    @Test
    void resolveDisplayText_shouldResolveMixedLabels_whenMultiDict() {
        when(dictItemService.getEnabledOptions("tag_type")).thenReturn(
                List.of(DictItemOptionVO.builder().code("A").label("标签A").build()));

        String text = exportDictLabelSupport.resolveDisplayText(FormFieldControlType.MULTI_DICT, "tag_type", "A,C");

        assertThat(text).isEqualTo("标签A、C");
    }

    /**
     * 非字典类控件（如 {@code TEXT}）应原样返回存储值，不触发字典解析。
     */
    @Test
    void resolveDisplayText_shouldKeepRawValue_whenNotDictType() {
        String text = exportDictLabelSupport.resolveDisplayText(FormFieldControlType.TEXT, null, "hello");

        assertThat(text).isEqualTo("hello");
    }

    /**
     * 原始值为空白时，应返回空字符串，不触发字典解析、不抛异常。
     */
    @Test
    void resolveDisplayText_shouldReturnEmptyString_whenRawValueBlank() {
        String text = exportDictLabelSupport.resolveDisplayText(FormFieldControlType.DICT, "gender", "");

        assertThat(text).isEmpty();
    }
}
