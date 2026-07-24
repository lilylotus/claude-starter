package cn.nihility.rbac.excelimport.service.support;

import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.service.DictItemService;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import cn.nihility.rbac.formfield.mapper.FormFieldDefinitionMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 供 Excel 模板生成、批量导入两条链路共用的字典列判定支持组件：识别导入字段配置中
 * 哪些列关联了字典下拉单选（{@code controlType=FormFieldControlType.DICT}）的表单
 * 字段定义，并提供该字典类型当前启用项的 label 列表、以及 label→code 的精确反查
 * （design.md Decision 1）。多选字典（{@code MULTI_DICT}）不在本次处理范围内，不会
 * 出现在本组件产出的字典列映射中。
 */
@Component
@RequiredArgsConstructor
public class DictImportColumnSupport {

    /** 表单字段定义数据访问接口，直接跨模块注入，用于按 id 批量查询 controlType/dictTypeCode。 */
    private final FormFieldDefinitionMapper formFieldDefinitionMapper;

    /** 字典项业务逻辑接口，用于查询字典类型当前启用状态的字典项。 */
    private final DictItemService dictItemService;

    /**
     * 从一组导入字段配置中识别出字典下拉单选列，产出 {@code fieldCode -> dictTypeCode}
     * 的映射；未关联表单字段定义、关联的表单字段定义控件类型不是 {@code DICT}、或
     * 未维护 {@code dictTypeCode} 的列不会出现在返回结果中。
     *
     * @param configs 导入字段配置列表
     * @return {@code fieldCode -> dictTypeCode} 映射，按 {@code configs} 原有顺序排列
     */
    public Map<String, String> resolveDictColumns(List<ImportFieldConfigVO> configs) {
        List<Long> definitionIds = configs.stream()
                .map(ImportFieldConfigVO::getFormFieldDefinitionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (definitionIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, FormFieldDefinitionEntity> definitionById = formFieldDefinitionMapper.selectByIds(definitionIds)
                .stream()
                .collect(Collectors.toMap(FormFieldDefinitionEntity::getId, Function.identity(), (left, right) -> left));

        Map<String, String> dictColumns = new LinkedHashMap<>();
        for (ImportFieldConfigVO config : configs) {
            FormFieldDefinitionEntity definition = definitionById.get(config.getFormFieldDefinitionId());
            if (definition == null || !Objects.equals(definition.getControlType(), FormFieldControlType.DICT)) {
                continue;
            }
            if (StringUtils.hasText(definition.getDictTypeCode())) {
                dictColumns.put(config.getFieldCode(), definition.getDictTypeCode());
            }
        }
        return dictColumns;
    }

    /**
     * 查询指定字典类型当前启用项的 label 列表，顺序与
     * {@link DictItemService#getEnabledOptions(String)} 返回顺序一致；无启用项时返回
     * 空列表。
     *
     * @param dictTypeCode 字典类型编码
     * @return label 列表
     */
    public List<String> getEnabledLabels(String dictTypeCode) {
        return dictItemService.getEnabledOptions(dictTypeCode).stream()
                .map(DictItemOptionVO::getLabel)
                .toList();
    }

    /**
     * 按 label 精确匹配反查指定字典类型当前启用项的 code；不做大小写/模糊匹配，也不
     * 兼容直接传入 code（design.md Decision 4）。
     *
     * @param dictTypeCode 字典类型编码
     * @param label        待反查的 label 文本
     * @return 匹配到的 code，未找到匹配项时返回 {@code null}
     */
    public String resolveCodeByLabel(String dictTypeCode, String label) {
        return dictItemService.getEnabledOptions(dictTypeCode).stream()
                .filter(option -> Objects.equals(option.getLabel(), label))
                .map(DictItemOptionVO::getCode)
                .findFirst()
                .orElse(null);
    }
}
