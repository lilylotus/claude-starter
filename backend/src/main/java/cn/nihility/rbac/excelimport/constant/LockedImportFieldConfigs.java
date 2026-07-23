package cn.nihility.rbac.excelimport.constant;

import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import java.util.Set;

/**
 * 锁定（系统保护）的导入字段配置白名单：POSITION 的 {@code __userCode}/
 * {@code __orgCode}、APP 的 {@code __ownerCode}/{@code __orgCode}、ORG 的
 * {@code __parentCode} 固定标识列均由数据库迁移预置，不属于管理员可以自由增删的
 * 配置项，因此在此维护一份 {@code (bizType, fieldCode)} 常量白名单，供服务层在
 * 更新/删除时反查计算得出一个只读的 {@code locked} 标记：锁定的配置不可删除、
 * 不可改绑表单字段定义、不可修改主键/必填标记（保持种子数据预置的原值，
 * POSITION/APP 的固定列均为 {@code isPrimaryKey=true}/{@code isRequired=true}，
 * ORG 的 {@code __parentCode} 则为 {@code isPrimaryKey=false}/
 * {@code isRequired=false}——顶级组织本无上级，允许留空），仅
 * {@code excelHeaderName}/{@code showOrder} 仍可调整（design.md Decision 2、
 * spec.md "POSITION 预置固定标识列"）。
 */
public final class LockedImportFieldConfigs {

    /** {@code (bizType, fieldCode)} 白名单，key 格式为 {@code bizType::fieldCode}。 */
    private static final Set<String> LOCKED_KEYS = Set.of(
            key(FormFieldBizType.POSITION, PositionPseudoFieldCode.USER_CODE),
            key(FormFieldBizType.POSITION, PositionPseudoFieldCode.ORG_CODE),
            key(FormFieldBizType.APP, AppPseudoFieldCode.OWNER_CODE),
            key(FormFieldBizType.APP, AppPseudoFieldCode.ORG_CODE),
            key(FormFieldBizType.ORG, OrgPseudoFieldCode.PARENT_CODE));

    /**
     * 工具类不允许实例化。
     */
    private LockedImportFieldConfigs() {
    }

    /**
     * 判断给定的业务对象类型与字段标识组合是否属于锁定（系统保护）的导入字段配置。
     *
     * @param bizType   业务对象类型
     * @param fieldCode 字段标识
     * @return 是否锁定
     */
    public static boolean isLocked(String bizType, String fieldCode) {
        return LOCKED_KEYS.contains(key(bizType, fieldCode));
    }

    /**
     * 拼装白名单内部使用的复合 key。
     *
     * @param bizType   业务对象类型
     * @param fieldCode 字段标识
     * @return 复合 key
     */
    private static String key(String bizType, String fieldCode) {
        return bizType + "::" + fieldCode;
    }
}
