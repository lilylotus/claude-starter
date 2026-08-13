package cn.nihility.rbac.sync.event.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据同步事件总线相关配置，绑定前缀 {@code rbac.sync}（app-sync-notify-pull-api change
 * design.md Decision 4 / tasks.md 6.5）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.sync")
public class SyncProperties {

    /** Disruptor 环形缓冲区大小，必须是 2 的幂，默认 1024。 */
    private int ringBufferSize = 1024;
}
