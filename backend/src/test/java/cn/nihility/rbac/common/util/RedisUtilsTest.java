package cn.nihility.rbac.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link RedisUtils} 的测试，起真实 Redis 连接（同项目既有"不做重量级 mock"的测试风格），
 * 覆盖字符串/对象存取（{@code opsForValue} 维度）与 Hash 字符串/对象字段存取
 * （{@code opsForHash} 维度）两组能力，以及 delete/hasKey/expire。
 */
@SpringBootTest
class RedisUtilsTest {

    /** 本测试用例使用的 Redis key 前缀，测试结束后统一清理。 */
    private static final String KEY_PREFIX = "test:redis-utils:";

    /** 直接注入的字符串 Redis 模板，仅用于测试结束后清理测试数据，不参与被测逻辑本身。 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 本用例写入的 key，测试结束后统一清理。 */
    private final List<String> writtenKeys = new ArrayList<>();

    /**
     * 每个用例结束后清理本用例写入的 Redis key，避免污染后续用例/占用 Redis 空间。
     */
    @AfterEach
    void cleanup() {
        writtenKeys.forEach(stringRedisTemplate::delete);
        writtenKeys.clear();
    }

    /**
     * 写入一个字符串值后，应能原样读取。
     */
    @Test
    void set_and_get_shouldRoundTripString() {
        String key = trackedKey("string");

        RedisUtils.set(key, "hello");

        assertThat(RedisUtils.get(key)).contains("hello");
    }

    /**
     * 读取一个不存在的 key，应返回空而不是抛出异常。
     */
    @Test
    void get_shouldReturnEmpty_whenKeyMissing() {
        assertThat(RedisUtils.get(trackedKey("missing"))).isEmpty();
    }

    /**
     * 写入一个对象后，应能反序列化为原始类型的等价对象（{@link RedisUtils#getObject(String, Class)}）。
     */
    @Test
    void setObject_and_getObject_shouldRoundTripByClass() {
        String key = trackedKey("object-class");
        Sample sample = new Sample("zhangsan", 18);

        RedisUtils.setObject(key, sample, 30, TimeUnit.SECONDS);

        assertThat(RedisUtils.getObject(key, Sample.class)).contains(sample);
    }

    /**
     * 写入一个对象后，应能按 {@link TypeReference} 反序列化。
     */
    @Test
    void setObject_and_getObject_shouldRoundTripByTypeReference() {
        String key = trackedKey("object-type-ref");
        Sample sample = new Sample("lisi", 20);

        RedisUtils.setObject(key, sample);

        assertThat(RedisUtils.getObject(key, new TypeReference<Sample>() {
        })).contains(sample);
    }

    /**
     * 删除一个 key 后，{@link RedisUtils#hasKey} 应返回 {@code false}。
     */
    @Test
    void delete_shouldRemoveKey() {
        String key = trackedKey("delete");
        RedisUtils.set(key, "value");

        Boolean deleted = RedisUtils.delete(key);

        assertThat(deleted).isTrue();
        assertThat(RedisUtils.hasKey(key)).isFalse();
    }

    /**
     * 给已存在的 key 设置过期时间后，短暂等待应自然过期。
     */
    @Test
    void expire_shouldMakeKeyExpireEventually() throws InterruptedException {
        String key = trackedKey("expire");
        RedisUtils.set(key, "value");

        RedisUtils.expire(key, 50, TimeUnit.MILLISECONDS);
        Thread.sleep(200);

        assertThat(RedisUtils.hasKey(key)).isFalse();
    }

    /**
     * 批量写入 Hash 多个字符串字段后，应能读出全部字段。
     */
    @Test
    void putHash_and_hashEntries_shouldRoundTrip() {
        String key = trackedKey("hash");

        RedisUtils.putHash(key, Map.of("a", "1", "b", "2"));

        Map<Object, Object> entries = RedisUtils.hashEntries(key);
        assertThat(entries).containsEntry("a", "1").containsEntry("b", "2");
    }

    /**
     * 写入 Hash 单个对象字段后，应能反序列化为原始类型的等价对象。
     */
    @Test
    void putHashObject_and_getHashObject_shouldRoundTrip() {
        String key = trackedKey("hash-object");
        Sample sample = new Sample("wangwu", 25);

        RedisUtils.putHashObject(key, "profile", sample);

        assertThat(RedisUtils.getHashObject(key, "profile", Sample.class)).contains(sample);
    }

    /**
     * 读取一个不存在的 Hash 字段，应返回空而不是抛出异常。
     */
    @Test
    void getHashObject_shouldReturnEmpty_whenFieldMissing() {
        String key = trackedKey("hash-object-missing");
        RedisUtils.putHashObject(key, "existing", new Sample("zhaoliu", 30));

        assertThat(RedisUtils.getHashObject(key, "not-existing", Sample.class)).isEmpty();
    }

    /**
     * 删除 Hash 单个字段后，应无法再读到该字段。
     */
    @Test
    void deleteHashField_shouldRemoveField() {
        String key = trackedKey("hash-delete-field");
        RedisUtils.putHashObject(key, "profile", new Sample("sunqi", 28));

        Long removed = RedisUtils.deleteHashField(key, "profile");

        assertThat(removed).isEqualTo(1L);
        assertThat(RedisUtils.getHashObject(key, "profile", Sample.class)).isEmpty();
    }

    /**
     * 首次调用 {@link RedisUtils#setIfAbsent} 时 key 不存在，应写入成功。
     */
    @Test
    void setIfAbsent_shouldSucceed_whenKeyMissing() {
        String key = trackedKey("set-if-absent-missing");

        boolean result = RedisUtils.setIfAbsent(key, "first", 30, TimeUnit.SECONDS);

        assertThat(result).isTrue();
        assertThat(RedisUtils.get(key)).contains("first");
    }

    /**
     * 二次调用 {@link RedisUtils#setIfAbsent} 时 key 已存在，应写入失败且不覆盖原值。
     */
    @Test
    void setIfAbsent_shouldFail_whenKeyAlreadyExists() {
        String key = trackedKey("set-if-absent-existing");
        RedisUtils.setIfAbsent(key, "first", 30, TimeUnit.SECONDS);

        boolean result = RedisUtils.setIfAbsent(key, "second", 30, TimeUnit.SECONDS);

        assertThat(result).isFalse();
        assertThat(RedisUtils.get(key)).contains("first");
    }

    /**
     * {@link RedisUtils#compareAndDelete} 当前值匹配时应删除成功。
     */
    @Test
    void compareAndDelete_shouldRemoveKey_whenValueMatches() {
        String key = trackedKey("compare-and-delete-match");
        RedisUtils.set(key, "token-1");

        boolean result = RedisUtils.compareAndDelete(key, "token-1");

        assertThat(result).isTrue();
        assertThat(RedisUtils.hasKey(key)).isFalse();
    }

    /**
     * {@link RedisUtils#compareAndDelete} 当前值不匹配时不应删除，原值保留。
     */
    @Test
    void compareAndDelete_shouldNotRemoveKey_whenValueMismatches() {
        String key = trackedKey("compare-and-delete-mismatch");
        RedisUtils.set(key, "token-1");

        boolean result = RedisUtils.compareAndDelete(key, "token-2");

        assertThat(result).isFalse();
        assertThat(RedisUtils.get(key)).contains("token-1");
    }

    /**
     * {@link RedisUtils#compareAndDelete} key 不存在时不应删除（也不应抛异常）。
     */
    @Test
    void compareAndDelete_shouldReturnFalse_whenKeyMissing() {
        boolean result = RedisUtils.compareAndDelete(trackedKey("compare-and-delete-missing"), "token-1");

        assertThat(result).isFalse();
    }

    /**
     * {@link RedisUtils#compareAndExpire} 当前值匹配时应续期成功。
     */
    @Test
    void compareAndExpire_shouldRenewTtl_whenValueMatches() {
        String key = trackedKey("compare-and-expire-match");
        RedisUtils.set(key, "token-1");

        boolean result = RedisUtils.compareAndExpire(key, "token-1", 30, TimeUnit.SECONDS);

        assertThat(result).isTrue();
        assertThat(RedisUtils.getExpire(key, TimeUnit.SECONDS)).isGreaterThan(0L);
    }

    /**
     * {@link RedisUtils#compareAndExpire} 当前值不匹配时不应续期。
     */
    @Test
    void compareAndExpire_shouldNotRenewTtl_whenValueMismatches() {
        String key = trackedKey("compare-and-expire-mismatch");
        RedisUtils.set(key, "token-1");

        boolean result = RedisUtils.compareAndExpire(key, "token-2", 30, TimeUnit.SECONDS);

        assertThat(result).isFalse();
        assertThat(RedisUtils.getExpire(key, TimeUnit.SECONDS)).isEqualTo(-1L);
    }

    /**
     * {@link RedisUtils#compareAndExpire} key 不存在时不应续期（也不应抛异常）。
     */
    @Test
    void compareAndExpire_shouldReturnFalse_whenKeyMissing() {
        boolean result = RedisUtils.compareAndExpire(trackedKey("compare-and-expire-missing"), "token-1", 30,
                TimeUnit.SECONDS);

        assertThat(result).isFalse();
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
     * 测试用简单对象，验证 {@link RedisUtils} 的对象序列化/反序列化能力。
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
