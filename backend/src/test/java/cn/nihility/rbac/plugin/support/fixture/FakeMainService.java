package cn.nihility.rbac.plugin.support.fixture;

/**
 * 测试用"主程序"业务接口，供插件覆盖测试使用（插件实现类在测试运行期动态编译，
 * 通过 {@code @PluginOverride(target = FakeMainService.class)} 声明覆盖）。
 */
public interface FakeMainService {

    /**
     * 返回当前生效实现的标识文案，用于断言覆盖是否生效。
     *
     * @return 标识文案
     */
    String identify();
}
