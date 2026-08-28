package cn.nihility.rbac.sync.event.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.sync.event.config.SyncProperties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** {@link SnowflakeIdGenerator} 的唯一性与时钟保护测试。 */
class SnowflakeIdGeneratorTest {

    /** 并发生成的事件标识必须全部唯一，但不对生成顺序作断言。 */
    @Test
    void nextId_shouldRemainUniqueUnderConcurrency() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(properties(7));
        Set<Long> ids = ConcurrentHashMap.newKeySet();

        IntStream.range(0, 20_000).parallel().forEach(index -> ids.add(generator.nextId()));

        assertThat(ids).hasSize(20_000);
    }

    /** 严重时钟回拨时必须明确失败，不能冒险生成重复标识。 */
    @Test
    void nextId_shouldFailWhenClockMovesBackTooFar() {
        ScriptedClockGenerator generator = new ScriptedClockGenerator(properties(1), 100L, 90L);
        generator.nextId();

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("时钟回拨");
    }

    private SyncProperties properties(long workerId) {
        SyncProperties properties = new SyncProperties();
        properties.setWorkerId(workerId);
        return properties;
    }

    /** 使用脚本时间源验证回拨分支。 */
    private static final class ScriptedClockGenerator extends SnowflakeIdGenerator {

        private final long[] timestamps;
        private int index;

        private ScriptedClockGenerator(SyncProperties properties, long... timestamps) {
            super(properties);
            this.timestamps = timestamps;
        }

        @Override
        protected long currentTimeMillis() {
            return timestamps[Math.min(index++, timestamps.length - 1)];
        }
    }
}
