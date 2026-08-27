package cn.nihility.rbac.plugin.support.fixture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 依赖 {@link FakeMainService} 的测试消费者，同时演示构造器注入与字段注入两种方式，
 * 用于验证插件覆盖后不论哪种注入方式拿到的都是插件实现（plugin-bean-override capability
 * spec "覆盖后的方法调用生效"）。
 */
@Service
public class FakeMainConsumer {

    /** 构造器注入的依赖。 */
    private final FakeMainService constructorInjected;

    /** 字段注入的依赖。 */
    @Autowired
    private FakeMainService fieldInjected;

    /**
     * 构造消费者。
     *
     * @param constructorInjected 构造器注入的 {@link FakeMainService}
     */
    public FakeMainConsumer(FakeMainService constructorInjected) {
        this.constructorInjected = constructorInjected;
    }

    /**
     * 返回构造器注入依赖的标识文案。
     *
     * @return 标识文案
     */
    public String delegateByConstructor() {
        return constructorInjected.identify();
    }

    /**
     * 返回字段注入依赖的标识文案。
     *
     * @return 标识文案
     */
    public String delegateByField() {
        return fieldInjected.identify();
    }
}
