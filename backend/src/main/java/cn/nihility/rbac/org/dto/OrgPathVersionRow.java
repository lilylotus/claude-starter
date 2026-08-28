package cn.nihility.rbac.org.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code OrgMapper#selectPathAndVersionByPrefix} 查询结果的内部投影载体（非对外 VO），
 * 每行代表按 {@code org_path} 前缀匹配到的一个组织（自身或子孙）在某一时刻的路径与版本，
 * 供 {@code OrgServiceImpl.update} 在 {@code parentId} 变化时，分别在级联更新前后各查一次，
 * 为受影响的每个组织计算出对应的 {@code orgScopePathBefore}/{@code orgScopePathAfter}
 * 和递增后的 {@code entityVersion}（app-sync-changelog-pull change design.md Decision 2）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgPathVersionRow {

    /** 组织 id。 */
    private Long id;

    /** 该组织在查询时刻的 {@code org_path}。 */
    private String orgPath;

    /** 该组织在查询时刻的同步实体版本。 */
    private Long version;
}
