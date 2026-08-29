package cn.nihility.rbac.sync.openapi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 对外同步接口限流与请求规模上限配置。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.sync.rate-limit")
public class SyncRateLimitProperties {

    private double pullTokensPerSecond = 10D;
    private int pullBurstCapacity = 30;
    private double digestTokensPerSecond = 1D;
    private int digestBurstCapacity = 3;
    private int maxPageSize = 500;
    private int maxIds = 200;
    private int maxOrgScopeRoots = 100;
}
