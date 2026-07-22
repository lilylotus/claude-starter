package cn.nihility.rbac.formfield.support;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.StringUtils;

/**
 * 组织/用户/任职/应用四个业务模块共用的动态字段校验支持工具，在保存前对
 * {@link cn.nihility.rbac.formfield.service.FormFieldDefinitionService#listActiveByBizType(String)}
 * 返回的非锁定字段定义依次执行必填、正则、唯一性校验（design.md Decision 9），
 * 避免四个模块各自重复实现同样的判断逻辑。字段值通过 Spring
 * {@link BeanWrapperImpl} 按属性名反射读取；这个属性名不是 {@code fieldCode}
 * （{@code fieldCode} 只是该字段定义自身的展示/标识别名，管理员可以为绑定
 * {@code ext6} 的定义自由取名为 {@code idCardNo} 之类，与请求 DTO 上真正存在的属性名
 * 无关），而是绑定的元数据字段列名（{@code columnName}）转换成驼峰形式后的结果：
 * {@code ext1}..{@code ext10} 本身无需转换，原有列（如 {@code id_card}、
 * {@code show_order}）转换后得到 {@code idCard}、{@code showOrder}，与
 * Create/Update 请求 DTO 上的 Java 属性名一致（见 design.md Decision 10）。
 */
public final class DynamicFieldValidator {

    /**
     * 工具类不允许实例化。
     */
    private DynamicFieldValidator() {
    }

    /**
     * 对给定的字段定义列表逐一执行必填、正则、（可选）唯一性校验。
     *
     * @param definitions   非锁定的字段定义列表，通常来自
     *                      {@code FormFieldDefinitionService.listActiveByBizType}
     * @param request       创建/更新请求对象，按绑定的元数据字段列名（转驼峰后）反射读取字段值
     * @param creating      {@code true} 表示新增场景（按 {@code showInCreate} 过滤），
     *                      {@code false} 表示编辑场景（按 {@code showInEdit} 过滤）
     * @param uniqueChecker 唯一性校验回调，仅在某条定义 {@code isUnique=true} 且值非空时调用；
     *                      传 {@code null} 表示调用方不支持/不需要唯一性校验
     */
    public static void validate(List<FormFieldDefinitionVO> definitions, Object request, boolean creating,
            UniqueChecker uniqueChecker) {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        BeanWrapper wrapper = new BeanWrapperImpl(request);
        for (FormFieldDefinitionVO definition : definitions) {
            boolean applicable = creating
                    ? Boolean.TRUE.equals(definition.getShowInCreate())
                    : Boolean.TRUE.equals(definition.getShowInEdit());
            if (!applicable) {
                continue;
            }

            String value = resolveValue(wrapper, toCamelCase(definition.getColumnName()));
            if (Boolean.TRUE.equals(definition.getIsRequired()) && !StringUtils.hasText(value)) {
                throw new BusinessException(definition.getFieldName() + "不能为空");
            }
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (StringUtils.hasText(definition.getValidateRegex())
                    && !Pattern.matches(definition.getValidateRegex(), value)) {
                throw new BusinessException(definition.getFieldName() + "格式不正确");
            }
            if (Boolean.TRUE.equals(definition.getIsUnique()) && uniqueChecker != null) {
                long count = uniqueChecker.count(definition.getColumnName(), value);
                if (count > 0) {
                    throw new BusinessException(definition.getFieldName() + "[" + value + "]已存在");
                }
            }
        }
    }

    /**
     * 按属性名从请求对象中反射读取字段值，读取失败（如属性不存在）时视为空值，
     * 不影响其余字段的校验。
     *
     * @param wrapper      请求对象的 Bean 包装器
     * @param propertyName 请求 DTO 上的 Java 属性名（驼峰形式）
     * @return 字段值的字符串表示，读取失败或值为 {@code null} 时返回 {@code null}
     */
    private static String resolveValue(BeanWrapper wrapper, String propertyName) {
        try {
            Object value = wrapper.getPropertyValue(propertyName);
            return value != null ? value.toString() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 把元数据字段的数据库列名（下划线分隔，如 {@code id_card}、{@code show_order}）
     * 转换成对应的 Java 驼峰属性名（{@code idCard}、{@code showOrder}）；
     * {@code ext1}..{@code ext10} 本身不含下划线，转换后保持不变。
     *
     * @param columnName 数据库列名
     * @return 驼峰形式的属性名
     */
    private static String toCamelCase(String columnName) {
        StringBuilder result = new StringBuilder(columnName.length());
        boolean upperNext = false;
        for (int i = 0; i < columnName.length(); i++) {
            char c = columnName.charAt(i);
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 唯一性校验回调，由调用方（各业务模块）绑定到自己的 Mapper
     * {@code countByColumnValue} 方法，屏蔽各模块 Mapper 类型差异。
     */
    @FunctionalInterface
    public interface UniqueChecker {

        /**
         * 统计指定列在调用方业务范围内（未逻辑删除、排除自身）等于给定值的记录数。
         *
         * @param columnName 元数据字段解析得到的列名，仅接受迁移写入的合法列名
         * @param value      待校验的字段值
         * @return 命中的记录数
         */
        long count(String columnName, String value);
    }
}
