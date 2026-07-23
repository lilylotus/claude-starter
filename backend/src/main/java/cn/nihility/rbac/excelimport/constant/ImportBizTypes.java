package cn.nihility.rbac.excelimport.constant;

import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import java.util.Set;

/**
 * Excel 导入能力当前覆盖的业务对象类型集合，直接复用
 * {@link cn.nihility.rbac.formfield.constant.FormFieldBizType} 的取值，避免另建
 * 一套重复的业务对象类型常量；仅额外提供一个校验用的集合，供导入字段配置管理、
 * 模板生成、批量导入三处共用同一份合法取值校验。
 */
public final class ImportBizTypes {

    /** 合法的业务对象类型取值集合：ORG/USER/POSITION/APP。 */
    public static final Set<String> ALL = Set.of(
            FormFieldBizType.ORG, FormFieldBizType.USER, FormFieldBizType.POSITION, FormFieldBizType.APP);

    /**
     * 工具类不允许实例化。
     */
    private ImportBizTypes() {
    }

    /**
     * 判断给定的业务对象类型取值是否合法。
     *
     * @param bizType 业务对象类型
     * @return 是否合法
     */
    public static boolean isValid(String bizType) {
        return bizType != null && ALL.contains(bizType);
    }
}
