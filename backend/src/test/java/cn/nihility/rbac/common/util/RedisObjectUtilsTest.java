package cn.nihility.rbac.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * {@link RedisObjectUtils} 的测试，起真实 Redis 连接（同项目既有"不做重量级 mock"的测试
 * 风格），覆盖 {@code opsForValue}/{@code opsForHash} 两个维度的对象 put/get，以及
 * delete/hasKey/expire。
 */
@SpringBootTest
class RedisObjectUtilsTest {

    /** 本测试用例使用的 Redis key 前缀，测试结束后统一清理。 */
    private static final String KEY_PREFIX = "test:redis-object-utils:";

    /** 直接注入的对象 Redis 模板，仅用于测试结束后清理测试数据，不参与被测逻辑本身。 */
    @Autowired
    @Qualifier("objectRedisTemplate")
    private RedisTemplate<String, Object> objectRedisTemplate;

    /** 本用例写入的 key，测试结束后统一清理。 */
    private final List<String> writtenKeys = new ArrayList<>();

    /**
     * 每个用例结束后清理本用例写入的 Redis key，避免污染后续用例/占用 Redis 空间。
     */
    @AfterEach
    void cleanup() {
        writtenKeys.forEach(objectRedisTemplate::delete);
        writtenKeys.clear();
    }

    /**
     * 写入一个对象后，应能按目标类型转换回等价对象（{@link RedisObjectUtils#get(String, Class)}）。
     */
    @Test
    void set_and_get_shouldRoundTripByClass() {
        String key = trackedKey("value-class");
        Sample sample = new Sample("zhangsan", 18);

        RedisObjectUtils.set(key, sample, 30, TimeUnit.SECONDS);

        assertThat(RedisObjectUtils.get(key, Sample.class)).contains(sample);
    }

    /**
     * 写入一个对象后，应能按 {@link TypeReference} 转换回等价对象。
     */
    @Test
    void set_and_get_shouldRoundTripByTypeReference() {
        String key = trackedKey("value-type-ref");
        Sample sample = new Sample("lisi", 20);

        RedisObjectUtils.set(key, sample);

        assertThat(RedisObjectUtils.get(key, new TypeReference<Sample>() {
        })).contains(sample);
    }

    /**
     * 不指定目标类型直接读取时，应能拿到非空的原始值。
     */
    @Test
    void get_withoutClass_shouldReturnRawValue() {
        String key = trackedKey("value-raw");
        RedisObjectUtils.set(key, new Sample("wangwu", 25));

        assertThat(RedisObjectUtils.get(key)).isPresent();
    }

    /**
     * 读取一个不存在的 key，应返回空而不是抛出异常。
     */
    @Test
    void get_shouldReturnEmpty_whenKeyMissing() {
        assertThat(RedisObjectUtils.get(trackedKey("missing"), Sample.class)).isEmpty();
    }

    /**
     * 删除一个 key 后，{@link RedisObjectUtils#hasKey} 应返回 {@code false}。
     */
    @Test
    void delete_shouldRemoveKey() {
        String key = trackedKey("delete");
        RedisObjectUtils.set(key, new Sample("zhaoliu", 30));

        Boolean deleted = RedisObjectUtils.delete(key);

        assertThat(deleted).isTrue();
        assertThat(RedisObjectUtils.hasKey(key)).isFalse();
    }

    /**
     * 给已存在的 key 设置过期时间后，短暂等待应自然过期。
     */
    @Test
    void expire_shouldMakeKeyExpireEventually() throws InterruptedException {
        String key = trackedKey("expire");
        RedisObjectUtils.set(key, new Sample("sunqi", 28));

        RedisObjectUtils.expire(key, 50, TimeUnit.MILLISECONDS);
        Thread.sleep(200);

        assertThat(RedisObjectUtils.hasKey(key)).isFalse();
    }

    /**
     * 写入 Hash 单个对象字段后，应能按目标类型转换回等价对象。
     */
    @Test
    void putHash_and_getHash_shouldRoundTrip() {
        String key = trackedKey("hash");
        Sample sample = new Sample("qianba", 32);

        RedisObjectUtils.putHash(key, "profile", sample);

        assertThat(RedisObjectUtils.getHash(key, "profile", Sample.class)).contains(sample);
    }

    /**
     * 读取一个不存在的 Hash 字段，应返回空而不是抛出异常。
     */
    @Test
    void getHash_shouldReturnEmpty_whenFieldMissing() {
        String key = trackedKey("hash-missing");
        RedisObjectUtils.putHash(key, "existing", new Sample("zhoujiu", 40));

        assertThat(RedisObjectUtils.getHash(key, "not-existing", Sample.class)).isEmpty();
    }

    /**
     * 批量写入 Hash 多个字段后，{@link RedisObjectUtils#hashEntries} 应能读出全部字段。
     */
    @Test
    void hashEntries_shouldReturnAllFields() {
        String key = trackedKey("hash-entries");
        RedisObjectUtils.putHash(key, "a", new Sample("a", 1));
        RedisObjectUtils.putHash(key, "b", new Sample("b", 2));

        assertThat(RedisObjectUtils.hashEntries(key)).hasSize(2).containsKeys("a", "b");
    }

    /**
     * 删除 Hash 单个字段后，应无法再读到该字段。
     */
    @Test
    void deleteHashField_shouldRemoveField() {
        String key = trackedKey("hash-delete-field");
        RedisObjectUtils.putHash(key, "profile", new Sample("wushi", 45));

        Long removed = RedisObjectUtils.deleteHashField(key, "profile");

        assertThat(removed).isEqualTo(1L);
        assertThat(RedisObjectUtils.getHash(key, "profile", Sample.class)).isEmpty();
    }

    /**
     * 生成一个带前缀的测试 key，并记录下来供 {@link #cleanup()} 统一清理。
     *
     * @param suffix key 后缀
     * @return 完整 key
     */
    private String trackedKey(String suffix) {
        String key = KEY_PREFIX + suffix;
        writtenKeys.add(key);
        return key;
    }

    /**
     * 测试用简单对象，验证 {@link RedisObjectUtils} 的对象存取能力。
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class Sample {

        /** 姓名。 */
        private String name;

        /** 年龄。 */
        private Integer age;

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Sample other)) {
                return false;
            }
            return Objects.equals(name, other.name) && Objects.equals(age, other.age);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age);
        }
    }
}
