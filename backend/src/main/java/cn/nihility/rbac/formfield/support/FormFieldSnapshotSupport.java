package cn.nihility.rbac.formfield.support;

import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.entity.DictTypeEntity;
import cn.nihility.rbac.dict.mapper.DictTypeMapper;
import cn.nihility.rbac.dict.service.DictItemService;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 组织/用户/任职/应用四个业务模块共用的操作日志字段快照扩展字段填充组件，把当前启用的
 * {@code ext1}..{@code ext10} 字段定义补充进各模块 {@code toLogSnapshot} 手工构建的
 * {@code Map<String, Object>} 快照里，key 使用字段定义的展示名（{@code fieldName}）而不是
 * {@code ext1} 这类技术列名，避免四个模块各自重复实现同样的过滤+写入逻辑（design.md
 * Decision 3）。对控件类型属于 {@link FormFieldControlType#DICT_TYPES}
 * （{@code DICT}/{@code MULTI_DICT}）的字段，会把存储的字典编码解析为人类可读的标签
 * 再写入快照，避免操作日志展示原始编码（design.md「6. 操作日志快照的字典值展示」）。
 */
@Component
@RequiredArgsConstructor
public class FormFieldSnapshotSupport {

    /** {@code ext1}..{@code ext10} 列名集合，用于过滤字段定义中绑定到扩展字段的部分。 */
    private static final Set<String> EXT_COLUMN_NAMES = Set.of(
            "ext1", "ext2", "ext3", "ext4", "ext5", "ext6", "ext7", "ext8", "ext9", "ext10");

    /** 多选字典下拉字段存储值的分隔符，与前端提交/回填的字符串格式保持一致。 */
    private static final String MULTI_DICT_VALUE_DELIMITER = ",";

    /** 多选字典下拉字段解析后展示文案的连接符，与前端 {@code dictOptionLabels()} 的展示风格一致。 */
    private static final String MULTI_DICT_LABEL_JOINER = "、";

    /** 字典类型数据访问接口，用于按 {@code dictTypeId} 反查字典类型编码。 */
    private final DictTypeMapper dictTypeMapper;

    /** 字典项业务逻辑接口，用于按字典类型编码查询启用字典项，解析编码到标签的映射。 */
    private final DictItemService dictItemService;

    /**
     * 把 {@code definitions} 中绑定到 {@code ext1}..{@code ext10} 的字段定义对应的当前值
     * 追加进快照 map，未配置字段定义的 {@code extN} 列不出现在快照里；控件类型为下拉单选
     * 字典或多选字典下拉的字段，追加的是解析后的标签而非原始存储编码。
     *
     * @param snapshot              待追加的操作日志字段快照，直接原地修改
     * @param definitions           当前启用的字段定义列表，通常来自
     *                              {@code FormFieldDefinitionService.listActiveByBizType}
     * @param extValuesByColumnName {@code ext1}..{@code ext10} 列名到实体当前对应值的映射
     */
    public void appendExtFieldSnapshot(Map<String, Object> snapshot, List<FormFieldDefinitionVO> definitions,
            Map<String, String> extValuesByColumnName) {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        for (FormFieldDefinitionVO definition : definitions) {
            String columnName = definition.getColumnName();
            if (columnName != null && EXT_COLUMN_NAMES.contains(columnName)) {
                String rawValue = extValuesByColumnName.get(columnName);
                snapshot.put(definition.getFieldName(), resolveDisplayValue(definition, rawValue));
            }
        }
    }

    /**
     * 根据字段定义的控件类型解析扩展字段当前值的展示文案：非字典类控件（{@code TEXT}/
     * {@code NUMBER}/{@code DATE}）或未关联字典类型时原样返回存储值；{@code DICT}
     * 按单个编码解析标签；{@code MULTI_DICT} 按逗号切分后逐个解析标签并用"、"重新拼接。
     * 找不到对应字典项标签的编码（如已停用/删除）回退展示原始编码。
     *
     * @param definition 字段定义
     * @param rawValue   扩展列当前的原始存储值
     * @return 展示文案
     */
    private String resolveDisplayValue(FormFieldDefinitionVO definition, String rawValue) {
        Integer controlType = definition.getControlType();
        Long dictTypeId = definition.getDictTypeId();
        if (rawValue == null || rawValue.isBlank() || !FormFieldControlType.DICT_TYPES.contains(controlType)
                || dictTypeId == null) {
            return rawValue;
        }
        Map<String, String> labelByCode = resolveLabelByCode(dictTypeId);
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
     * 按字典类型 id 查询其编码，再按编码查询启用字典项，构造编码到标签的映射；
     * 字典类型不存在时返回空 map，交由调用方回退展示原始编码。
     *
     * @param dictTypeId 字典类型 id
     * @return 字典项编码到标签的映射
     */
    private Map<String, String> resolveLabelByCode(Long dictTypeId) {
        DictTypeEntity dictType = dictTypeMapper.selectById(dictTypeId);
        if (dictType == null) {
            return Map.of();
        }
        List<DictItemOptionVO> options = dictItemService.getEnabledOptions(dictType.getCode());
        return options.stream()
                .collect(Collectors.toMap(
                        DictItemOptionVO::getCode, DictItemOptionVO::getLabel, (left, right) -> left));
    }
}
