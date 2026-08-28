package cn.nihility.rbac.excelexport.support;

import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.service.DictItemService;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 导出 Excel 场景下把字典/多选字典下拉字段的原始存储编码解析为展示标签的支持组件，
 * 复用 {@link DictItemService#getEnabledOptions(String)} 得到 code -&gt; label 映射。
 * 分隔符、拼接符、找不到匹配时的回退规则与
 * {@link cn.nihility.rbac.formfield.support.FormFieldSnapshotSupport}（操作日志字段
 * 快照）保持一致，确保导出结果、操作日志、页面展示三处口径统一（design.md
 * Decision 6）；本组件是一个通用的"给定 controlType/dictTypeCode/原始存储值，返回
 * 展示文案"纯函数，不像 {@code FormFieldSnapshotSupport} 那样绑定 {@code ext1}~
 * {@code ext10} 快照 map 结构，可服务于导出场景按任意 {@code columnName} 取值后再
 * 换算的需求。
 */
@Component
@RequiredArgsConstructor
public class ExportDictLabelSupport {

    /** 多选字典下拉字段存储值的分隔符，与前端提交/回填的字符串格式保持一致。 */
    private static final String MULTI_DICT_VALUE_DELIMITER = ",";

    /** 多选字典下拉字段解析后展示文案的连接符，与前端 {@code dictOptionLabels()} 的展示风格一致。 */
    private static final String MULTI_DICT_LABEL_JOINER = "、";

    /** 字典项业务逻辑接口，用于按字典类型编码查询启用字典项，解析编码到标签的映射。 */
    private final DictItemService dictItemService;

    /**
     * 解析一个字段当前值的导出展示文案：非字典类控件（{@code controlType} 不在
     * {@link FormFieldControlType#DICT_TYPES} 内）或未关联字典类型时原样返回原始值；
     * {@code DICT} 按单个编码解析标签；{@code MULTI_DICT} 按逗号切分后逐个解析标签并
     * 用"、"重新拼接。找不到对应字典项标签的编码（如已停用/删除）回退展示原始编码，
     * 不留空、不报错。
     *
     * @param controlType  控件类型
     * @param dictTypeCode 关联的字典类型编码
     * @param rawValue     原始存储值，可为空
     * @return 导出展示文案，{@code rawValue} 为空白时返回空字符串
     */
    public String resolveDisplayText(Integer controlType, String dictTypeCode, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }
        if (!FormFieldControlType.DICT_TYPES.contains(controlType) || dictTypeCode == null) {
            return rawValue;
        }
        Map<String, String> labelByCode = resolveLabelByCode(dictTypeCode);
        if (Objects.equals(controlType, FormFieldControlType.MULTI_DICT)) {
            return Arrays.stream(rawValue.split(MULTI_DICT_VALUE_DELIMITER))
                    .map(String::trim)
                    .filter(code -> !code.isEmpty())
                    .map(code -> labelByCode.getOrDefault(code, code))
                    .collect(Collectors.joining(MULTI_DICT_LABEL_JOINER));
        }
        return labelByCode.getOrDefault(rawValue, rawValue);
    }

    /**
     * 按字典类型编码查询启用字典项，构造编码到标签的映射。
     *
     * @param dictTypeCode 字典类型编码
     * @return 字典项编码到标签的映射
     */
    private Map<String, String> resolveLabelByCode(String dictTypeCode) {
        return dictItemService.getEnabledOptions(dictTypeCode).stream()
                .collect(Collectors.toMap(
                        DictItemOptionVO::getCode, DictItemOptionVO::getLabel, (left, right) -> left));
    }
}
