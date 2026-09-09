package cn.nihility.rbac.ratelimit.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import cn.nihility.rbac.common.util.RedisUtils;
import cn.nihility.rbac.ratelimit.annotation.RateLimit;
import cn.nihility.rbac.ratelimit.config.RbacRateLimitProperties;
import cn.nihility.rbac.ratelimit.exception.RateLimitExceededException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link RateLimitAspect} 单元测试，覆盖 api-rate-limiting spec.md 的全部 Scenario：
 * 方法级覆盖参数生效、未覆盖时使用全局默认、不同 IP 互不影响、不同接口互不影响、达到上限后
 * 拒绝、窗口过期后计数重置（add-captcha-rate-limit change tasks.md 4.3）。用一个内存
 * {@link Map} 模拟 Redis {@code INCR} 计数器，通过 {@link AspectJProxyFactory} 把
 * {@link RateLimitAspect} 织入测试目标 bean（不需要启动 Spring 容器），验证
 * {@code @Around} 环绕通知的真实拦截行为。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    /** 本类执行前 {@link RedisUtils} 生效的原始模板，用例结束后还原。 */
    private static StringRedisTemplate originalStringRedisTemplate;

    /** 被测拦截器经由 {@link RedisUtils} 间接依赖的 Redis 模板，使用 Mockito 打桩。 */
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /** 字符串值操作依赖，使用 Mockito 打桩。 */
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 模拟 Redis 中固定窗口计数器的内存存储：key -&gt; 当前计数值。 */
    private final Map<String, Long> counters = new HashMap<>();

    /** 全局默认限流配置，可在各用例中按需覆盖。 */
    private RbacRateLimitProperties rateLimitProperties;

    /** 经代理包装后的测试目标，标注了 {@link RateLimit} 的方法会被限流拦截。 */
    private RateLimitedTarget proxy;

    /**
     * 本类第一个用例执行前保存 {@link RedisUtils} 当前生效的原始模板。
     */
    @BeforeAll
    static void captureOriginalRedisUtilsState() {
        originalStringRedisTemplate = RedisUtils.current();
    }

    /**
     * 本类全部用例执行完毕后把 {@link RedisUtils} 还原为原始模板。
     */
    @AfterAll
    static void restoreRedisUtils() {
        RedisUtils.configure(originalStringRedisTemplate);
    }

    /**
     * 每个用例执行前重新构造被测拦截器、代理，并把 Mockito 打桩的 Redis 计数操作接到内存
     * Map 上。
     */
    @BeforeEach
    void setUp() {
        counters.clear();
        rateLimitProperties = new RbacRateLimitProperties();

        RateLimitAspect rateLimitAspect = new RateLimitAspect(rateLimitProperties);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new RateLimitedTarget());
        proxyFactory.addAspect(rateLimitAspect);
        proxy = proxyFactory.getProxy();

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.increment(anyString()))
                .thenAnswer(invocation -> counters.merge(invocation.getArgument(0), 1L, Long::sum));
        lenient().when(stringRedisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        RedisUtils.configure(stringRedisTemplate);
    }

    /**
     * 每个用例结束后清理请求上下文，避免影响后续用例或其它测试类。
     */
    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 方法级覆盖限流参数（10 秒内最多 3 次）生效：第 4 次及以后的请求被拒绝。
     */
    @Test
    void override_shouldRejectAfterConfiguredMaxRequests() {
        useClientIp("10.0.0.1");

        proxy.overridden();
        proxy.overridden();
        proxy.overridden();

        assertThatThrownBy(proxy::overridden).isInstanceOf(RateLimitExceededException.class);
    }

    /**
     * 未覆盖时间窗口/最大请求次数时，应使用全局默认配置。
     */
    @Test
    void default_shouldUseGlobalConfigWhenNotOverridden() {
        rateLimitProperties.setWindowSeconds(10);
        rateLimitProperties.setMaxRequests(2);
        useClientIp("10.0.0.2");

        proxy.defaultLimited();
        proxy.defaultLimited();

        assertThatThrownBy(proxy::defaultLimited).isInstanceOf(RateLimitExceededException.class);
    }

    /**
     * 不同 IP 的计数相互独立：IP A 达到上限不影响 IP B 的首次请求。
     */
    @Test
    void differentIp_shouldNotAffectEachOther() {
        useClientIp("10.0.0.3");
        proxy.singleRequestLimit();
        assertThatThrownBy(proxy::singleRequestLimit).isInstanceOf(RateLimitExceededException.class);

        useClientIp("10.0.0.4");
        assertThat(proxy.singleRequestLimit()).isEqualTo("ok");
    }

    /**
     * 不同接口（不同注解标注点）的计数相互独立：接口甲达到上限不影响接口乙的计数。
     */
    @Test
    void differentMethod_shouldNotAffectEachOther() {
        useClientIp("10.0.0.5");

        proxy.singleRequestLimit();
        assertThatThrownBy(proxy::singleRequestLimit).isInstanceOf(RateLimitExceededException.class);

        assertThat(proxy.anotherSingleRequestLimit()).isEqualTo("ok");
    }

    /**
     * 达到窗口内最大请求次数后，再次请求被拒绝，不执行业务逻辑。
     */
    @Test
    void shouldRejectRequest_whenMaxRequestsReachedInWindow() {
        useClientIp("10.0.0.6");
        proxy.singleRequestLimit();

        assertThatThrownBy(proxy::singleRequestLimit).isInstanceOf(RateLimitExceededException.class);
    }

    /**
     * 时间窗口过期后，计数应被重置：模拟 Redis key 因 TTL 到期而失效（内存计数器清空），
     * 下一次请求应恢复放行。
     */
    @Test
    void windowExpiry_shouldResetCounterAfterWindowPasses() {
        useClientIp("10.0.0.7");
        proxy.singleRequestLimit();
        assertThatThrownBy(proxy::singleRequestLimit).isInstanceOf(RateLimitExceededException.class);

        // 模拟窗口过期：Redis 中对应 key 的 TTL 到期后被自动清除。
        counters.clear();

        assertThat(proxy.singleRequestLimit()).isEqualTo("ok");
    }

    /**
     * 把当前线程绑定的请求上下文替换为携带指定客户端 IP 的模拟请求，供
     * {@link RateLimitAspect} 内部通过 {@code ClientRequestUtils.resolveClientIp} 解析。
     *
     * @param clientIp 模拟的客户端 IP
     */
    private void useClientIp(String clientIp) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(clientIp);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /**
     * 测试用限流目标类，标注不同的 {@link RateLimit} 配置供各 Scenario 使用。
     */
    static class RateLimitedTarget {

        /** 方法级覆盖为 10 秒内最多 3 次。 */
        @RateLimit(windowSeconds = 10, maxRequests = 3)
        public String overridden() {
            return "ok";
        }

        /** 未覆盖参数，使用全局默认配置。 */
        @RateLimit
        public String defaultLimited() {
            return "ok";
        }

        /** 窗口内最多 1 次请求，供限流边界类 Scenario 使用。 */
        @RateLimit(windowSeconds = 10, maxRequests = 1)
        public String singleRequestLimit() {
            return "ok";
        }

        /** 与 {@link #singleRequestLimit()} 独立的另一个接口，同样限制窗口内最多 1 次。 */
        @RateLimit(windowSeconds = 10, maxRequests = 1)
        public String anotherSingleRequestLimit() {
            return "ok";
        }
    }
}
