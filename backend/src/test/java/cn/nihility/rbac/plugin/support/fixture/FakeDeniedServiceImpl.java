package cn.nihility.rbac.plugin.support.fixture;

import org.springframework.stereotype.Service;

/**
 * {@link FakeDeniedService} 的"主程序"默认实现，覆盖黑名单命中场景下应始终保持生效。
 */
@Service
public class FakeDeniedServiceImpl implements FakeDeniedService {

    /**
     * {@inheritDoc}
     */
    @Override
    public String identify() {
        return "main-denied";
    }
}
