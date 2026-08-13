package cn.nihility.rbac.identity.upstream.constant;

/**
 * 组织（ORG）数据域同步专用的固定伪字段编码。组织树的上级/下级层级关系是本系统组织
 * 管理的核心概念（{@code OrgManagement}、组织范围权限等大量功能都建立在 {@code parentId}
 * 层级之上），但"上级组织编码"既不是 ORG bizType 下的元数据字段（元数据字段目录只描述
 * {@code tab_org} 自身的开放配置列，不包含表达层级关系的 {@code parentId}），
 * {@code OrgCreateRequest}/{@code OrgUpdateRequest} 上对应的可写属性也是内部数据库主键
 * {@code parentId} 而不是人可读的编码，上游系统不可能知道这个数字，因此无法通过"上游
 * 字段映射"配置的常规 metadataFieldId 目标来传递。
 *
 * <p>与 {@link UpstreamPositionPseudoFieldCode} 是同一类问题（外键/层级关系不是可开放
 * 配置的元数据字段，字段映射机制天生传不过去），采用同一套解法：比照 Excel 批量导入
 * {@code cn.nihility.rbac.excelimport.constant.OrgPseudoFieldCode#PARENT_CODE} 的既有
 * 解法，为组织数据域约定一个固定的"上游字段编码"（即取数 SQL 的列别名 / 接口 JSON 的
 * 字段名，不经过字段映射表配置，直接从取数阶段拉取到的原始行中按约定键名读取）。
 *
 * <p>与 Excel 导入不同的一点（design.md Decision 3 补充说明）：Excel 导入场景该列强制
 * 必填（模板本身有"固定必填列"的前置保障机制），取值为空即判定整行失败；上游同步场景
 * 没有这层保障，管理员完全可能只想同步一批平级组织、不关心层级，因此取不到该编码或
 * 取值为空时视为顶级组织（{@code parentId=0}），不判定失败；取值字面为 {@code "0"} 时
 * 同样落地为顶级组织；其余取值按 {@code tab_org.code} 匹配已有组织得到 {@code parentId}，
 * 匹配不到时该行判定失败。
 */
public final class UpstreamOrgPseudoFieldCode {

    /**
     * 上级组织编码伪字段编码，管理员配置 ORG 数据域的取数 SQL/接口时，可选地将其中一列
     * 按该固定编码命名，取值按组织 {@code code} 精确匹配得到 {@code parentId}；取不到该
     * 编码、取值为空或字面为 {@code "0"} 均表示顶级组织。
     */
    public static final String PARENT_CODE = "__parentCode";

    /**
     * 工具类不允许实例化。
     */
    private UpstreamOrgPseudoFieldCode() {
    }
}
