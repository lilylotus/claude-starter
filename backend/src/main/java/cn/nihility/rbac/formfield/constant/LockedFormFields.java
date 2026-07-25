package cn.nihility.rbac.formfield.constant;

import java.util.Set;

/**
 * 承重字段（锁定字段）白名单：组织/用户/应用各自的 {@code name}、{@code code} 列，
 * 以及任职（POSITION）的 {@code position_type} 列，当前已经拥有后端硬编码的
 * {@code @NotBlank} 与业务规则校验（编码唯一性，或如 {@code position_type} 对应
 * {@code tab_user_position.position_type} 列本身为 {@code NOT NULL} 且创建任职记录
 * 接口已把其列为硬编码必填项），属于代码层面的既定事实，不属于管理员可以在界面上
 * 调整的"元数据配置"，因此不落库到
 * {@code tab_metadata_field}/{@code tab_form_field_definition}，改为在此维护一份
 * {@code (bizType, columnName)} 常量白名单，绑定到这些元数据字段的表单字段定义
 * 在读取/更新时通过反查计算得出一个只读的 {@code locked} 标记，不可停用/删除，
 * 也不可把"是否必填"/"是否新增或编辑表单展示"配置为否。
 */
public final class LockedFormFields {

    /** {@code (bizType, columnName)} 白名单，key 格式为 {@code bizType::columnName}。 */
    private static final Set<String> LOCKED_KEYS = Set.of(
            key(FormFieldBizType.ORG, "name"),
            key(FormFieldBizType.ORG, "code"),
            key(FormFieldBizType.USER, "name"),
            key(FormFieldBizType.USER, "code"),
            key(FormFieldBizType.APP, "name"),
            key(FormFieldBizType.APP, "code"),
            key(FormFieldBizType.POSITION, "position_type"));

    /**
     * 工具类不允许实例化。
     */
    private LockedFormFields() {
    }

    /**
     * 判断给定的业务对象类型与列名组合是否属于承重字段（锁定字段）。
     *
     * @param bizType    业务对象类型，见 {@link FormFieldBizType}
     * @param columnName 元数据字段的列名
     * @return 是否为承重字段
     */
    public static boolean isLocked(String bizType, String columnName) {
        return LOCKED_KEYS.contains(key(bizType, columnName));
    }

    /**
     * 拼装白名单内部使用的复合 key。
     *
     * @param bizType    业务对象类型
     * @param columnName 列名
     * @return 复合 key
     */
    private static String key(String bizType, String columnName) {
        return bizType + "::" + columnName;
    }
}
