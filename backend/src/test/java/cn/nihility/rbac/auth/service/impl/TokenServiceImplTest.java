package cn.nihility.rbac.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.config.RbacLoginProperties;
import cn.nihility.rbac.auth.constant.AuthErrorCode;
import cn.nihility.rbac.auth.dto.TokenPair;
import cn.nihility.rbac.common.exception.BusinessException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * {@link TokenServiceImpl} 的单元测试，覆盖签发/校验/刷新会话令牌的核心分支
 * （password-login-auth spec.md "口令登录"/"access-key 刷新"相关 Scenario）。
 * 用一个内存 {@link Map} 模拟 Redis 中 {@code user:identity-token:<userId>} 这条会话 Hash，
 * 使断言可以直接读取该 Hash 的最新内容，而不必逐个字段单独打桩。
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    /** 被测服务的 Redis 模板依赖，使用 Mockito 打桩。 */
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /** 字符串值操作依赖，使用 Mockito 打桩。 */
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** Hash 操作依赖，使用 Mockito 打桩。 */
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    /** 模拟 Redis 中的字符串键值存储（access-token/refresh-token 反查记录）。 */
    private final Map<String, String> stringStore = new HashMap<>();

    /** 模拟 Redis 中的会话 Hash 存储（identity-token）。 */
    private final Map<String, Map<Object, Object>> hashStore = new HashMap<>();

    /** 登录相关配置。 */
    private RbacLoginProperties loginProperties;

    /** 被测服务实例。 */
    private TokenServiceImpl tokenService;

    /**
     * 每个用例执行前重新构造被测服务，并把 Mockito 打桩的 Redis 操作接口接到内存 Map 上。
     */
    @BeforeEach
    void setUp() {
        stringStore.clear();
        hashStore.clear();

        loginProperties = new RbacLoginProperties();
        loginProperties.setAccessTokenExpireSeconds(7200);
        loginProperties.setRefreshTokenExpireSeconds(604800);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        lenient().doAnswer(invocation -> stringStore.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(valueOperations).set(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any());
        lenient().when(valueOperations.get(anyString())).thenAnswer(invocation -> stringStore.get(invocation.getArgument(0)));

        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Map<Object, Object> value = invocation.getArgument(1);
            hashStore.computeIfAbsent(key, k -> new HashMap<>()).putAll(value);
            return null;
        }).when(hashOperations).putAll(anyString(), org.mockito.ArgumentMatchers.anyMap());
        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object field = invocation.getArgument(1);
            Object value = invocation.getArgument(2);
            hashStore.computeIfAbsent(key, k -> new HashMap<>()).put(field, value);
            return null;
        }).when(hashOperations).put(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        lenient().when(hashOperations.entries(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return hashStore.getOrDefault(key, Map.of());
        });

        tokenService = new TokenServiceImpl(stringRedisTemplate, loginProperties);
    }

    /**
     * 签发令牌后，应能通过反查记录 + 会话 Hash 校验出对应的用户 id。
     */
    @Test
    void issue_shouldMakeAccessKeyVerifiable() {
        TokenPair tokenPair = tokenService.issue(1L);

        assertThat(tokenPair.getAccessKey()).doesNotContain("-");
        assertThat(tokenPair.getRefreshKey()).doesNotContain("-");
        Optional<Long> userId = tokenService.verifyAccessKey(tokenPair.getAccessKey());
        assertThat(userId).contains(1L);
    }

    /**
     * 校验一个未曾签发过的 access-key，应返回空（未登录）。
     */
    @Test
    void verifyAccessKey_shouldReturnEmpty_whenAccessKeyUnknown() {
        assertThat(tokenService.verifyAccessKey("unknown-access-key")).isEmpty();
    }

    /**
     * 刷新成功后，旧 access-key 应立即失效（会话 Hash 中记录的 accessKey 已被新值覆盖），
     * 新 access-key 校验通过（spec.md "refresh-key 有效时刷新成功" Scenario）。
     */
    @Test
    void refresh_shouldInvalidateOldAccessKeyAndIssueNewOne() {
        TokenPair initial = tokenService.issue(1L);

        TokenPair refreshed = tokenService.refresh(initial.getRefreshKey());

        assertThat(refreshed.getAccessKey()).isNotEqualTo(initial.getAccessKey());
        assertThat(refreshed.getRefreshKey()).isEqualTo(initial.getRefreshKey());
        assertThat(tokenService.verifyAccessKey(initial.getAccessKey())).isEmpty();
        assertThat(tokenService.verifyAccessKey(refreshed.getAccessKey())).contains(1L);
    }

    /**
     * 使用无效/未知的 refresh-key 刷新时，应抛出未登录业务异常，不签发新的 access-key
     * （spec.md "refresh-key 已过期时刷新失败" Scenario）。
     */
    @Test
    void refresh_shouldThrowBusinessException_whenRefreshKeyUnknown() {
        assertThatThrownBy(() -> tokenService.refresh("unknown-refresh-key"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(AuthErrorCode.UNAUTHORIZED));
    }
}
