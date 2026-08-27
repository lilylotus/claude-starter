package cn.nihility.rbac.plugin.support.fixture;

/**
 * 测试用"安全关键"业务接口，模拟覆盖黑名单命中场景（plugin-bean-override capability spec
 * "覆盖范围限制"）；测试中把本接口全限定名加入 {@code rbac.plugin.override.deny-list}。
 */
public interface FakeDeniedService {

    /**
     * 返回当前生效实现的标识文案。
     *
     * @return 标识文案
     */
    String identify();
}
