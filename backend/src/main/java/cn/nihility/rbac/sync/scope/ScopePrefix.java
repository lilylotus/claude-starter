package cn.nihility.rbac.sync.scope;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一条"原始范围前缀"，对应 {@code tab_app_sync_org_scope} 一行配置解析出的组织当前
 * {@code orgPath}（app-sync-changelog-pull change design.md Decision 4）：与
 * {@code AppSyncOrgScopeResolver#resolveAllowedOrgIds} 返回"展开后的 id 全集"不同，本类型
 * 保留原始路径前缀与是否包含子孙的语义，供 {@code /changes} 接口在 MyBatis XML 里拼装
 * 边界安全的路径前缀条件（{@code path = prefix OR path LIKE CONCAT(prefix, '/%')}）。用普通
 * Java 类（而非 record）声明，保证 MyBatis 通过标准 JavaBean {@code getOrgPath()}/
 * {@code getIncludeChildren()} 反射访问属性时行为可预期。
 */
@Getter
@AllArgsConstructor
public class ScopePrefix {

    /** 组织当前的 {@code orgPath}，仅允许数字与 {@code /}，不含 {@code %}/{@code _} 等 LIKE 通配字符。 */
    private final String orgPath;

    /** 是否包含递归子孙组织：{@code true} 时前缀匹配需要额外命中 {@code orgPath} 前缀下的子孙路径。 */
    private final boolean includeChildren;
}
