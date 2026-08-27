package cn.nihility.rbac.plugin.support.fixture;

import org.springframework.stereotype.Service;

/**
 * {@link FakeMainService} 的"主程序"默认实现，作为插件覆盖测试的覆盖目标。
 */
@Service
public class FakeMainServiceImpl implements FakeMainService {

    /**
     * {@inheritDoc}
     */
    @Override
    public String identify() {
        return "main";
    }
}
