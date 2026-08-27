package cn.nihility.rbac.plugin.support;

import java.util.Set;

/**
 * 覆盖黑名单校验（plugin-bean-override capability spec "覆盖范围限制"，design.md
 * Decision 5）：命中 {@code rbac.plugin.override.deny-list} 的目标类禁止被插件覆盖。
 */
public class OverrideDenyListChecker {

    /** 黑名单目标类全限定名集合。 */
    private final Set<String> denyList;

    /**
     * 构造校验器。
     *
     * @param denyList 黑名单目标类全限定名集合
     */
    public OverrideDenyListChecker(Set<String> denyList) {
        this.denyList = denyList;
    }

    /**
     * 判断目标类是否命中覆盖黑名单。
     *
     * @param targetClassName 覆盖目标类全限定名
     * @return 是否命中黑名单
     */
    public boolean isDenied(String targetClassName) {
        return denyList.contains(targetClassName);
    }
}
