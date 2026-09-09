package cn.nihility.rbac.captcha.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import cn.nihility.rbac.captcha.config.RbacCaptchaProperties;
import cn.nihility.rbac.captcha.constant.CaptchaType;
import cn.nihility.rbac.captcha.dto.CaptchaImageVO;
import cn.nihility.rbac.captcha.support.CaptchaAnswerPayload;
import cn.nihility.rbac.captcha.support.CaptchaContentGenerator;
import cn.nihility.rbac.captcha.support.CaptchaGenerationResult;
import cn.nihility.rbac.captcha.support.CaptchaImageRenderer;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.common.util.RedisUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * {@link ImageCaptchaServiceImpl} 单元测试，覆盖 image-captcha spec.md 的全部 Scenario：
 * 字符模式默认/自定义长度生成、生成内容不含易混淆字符、算式模式除法整除、答案正确/错误/
 * 不存在/重复校验四类一次性校验分支。Redis 依赖打桩方式与
 * {@code TokenServiceImplTest} 一致——用内存 {@link Map} 模拟字符串键值存储。
 */
@ExtendWith(MockitoExtension.class)
class ImageCaptchaServiceImplTest {

    /** 本类执行前 {@link RedisUtils} 生效的原始模板，用例结束后还原。 */
    private static StringRedisTemplate originalStringRedisTemplate;

    /** 被测服务经由 {@link RedisUtils} 间接依赖的 Redis 模板，使用 Mockito 打桩。 */
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /** 字符串值操作依赖，使用 Mockito 打桩。 */
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 模拟 Redis 中的字符串键值存储。 */
    private final Map<String, String> store = new HashMap<>();

    /** 图形验证码相关配置，可在各用例中按需覆盖。 */
    private RbacCaptchaProperties captchaProperties;

    /** 被测服务实例。 */
    private ImageCaptchaServiceImpl imageCaptchaService;

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
     * 每个用例执行前重新构造被测服务，并把 Mockito 打桩的 Redis 操作接口接到内存 Map 上。
     */
    @BeforeEach
    void setUp() {
        store.clear();
        captchaProperties = new RbacCaptchaProperties();

        CaptchaContentGenerator captchaContentGenerator = new CaptchaContentGenerator(captchaProperties);
        CaptchaImageRenderer captchaImageRenderer = new CaptchaImageRenderer();
        imageCaptchaService = new ImageCaptchaServiceImpl(captchaProperties, captchaContentGenerator, captchaImageRenderer);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), anyLong(), any());
        lenient().when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> store.get((String) invocation.getArgument(0)));
        lenient().when(stringRedisTemplate.delete(anyString()))
                .thenAnswer(invocation -> store.remove((String) invocation.getArgument(0)) != null);

        RedisUtils.configure(stringRedisTemplate);
    }

    /**
     * 字符模式下未覆盖默认字符个数时，应生成 4 位字符验证码，且答案已写入 Redis。
     */
    @Test
    void generate_shouldStoreDefaultLengthCharacterAnswer() {
        captchaProperties.setType(CaptchaType.CHARACTER);
        captchaProperties.setCharacterLength(4);

        CaptchaImageVO vo = imageCaptchaService.generate();

        assertThat(vo.getCaptchaId()).isNotBlank();
        assertThat(vo.getImageBase64()).isNotBlank();
        CaptchaAnswerPayload payload = readPayload(vo.getCaptchaId());
        assertThat(payload.type()).isEqualTo(CaptchaType.CHARACTER);
        assertThat(payload.answer()).hasSize(4);
    }

    /**
     * 字符个数被覆盖为 6 时，生成的答案长度应同为 6。
     */
    @Test
    void generate_shouldRespectCustomCharacterLength() {
        captchaProperties.setType(CaptchaType.CHARACTER);
        captchaProperties.setCharacterLength(6);

        CaptchaImageVO vo = imageCaptchaService.generate();

        CaptchaAnswerPayload payload = readPayload(vo.getCaptchaId());
        assertThat(payload.answer()).hasSize(6);
    }

    /**
     * 字符模式生成内容不应出现易混淆字符（0、1、O、o、I、i、l），多次生成以充分覆盖候选
     * 字符集。
     */
    @Test
    void generate_character_shouldNotContainAmbiguousCharacters() {
        captchaProperties.setType(CaptchaType.CHARACTER);
        captchaProperties.setCharacterLength(8);

        Set<Character> seenCharacters = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            CaptchaImageVO vo = imageCaptchaService.generate();
            String answer = readPayload(vo.getCaptchaId()).answer();
            for (char c : answer.toCharArray()) {
                seenCharacters.add(c);
            }
        }

        for (char ambiguous : "01OoIil".toCharArray()) {
            assertThat(seenCharacters).doesNotContain(ambiguous);
        }
    }

    /**
     * 算式模式下随机选中除法运算符时，生成的算式应能整除，展示文本与答案一致。
     */
    @Test
    void generate_arithmetic_divide_shouldProduceIntegerResult() {
        captchaProperties.setType(CaptchaType.ARITHMETIC);
        CaptchaContentGenerator generator = new CaptchaContentGenerator(captchaProperties);

        boolean hitDivide = false;
        for (int i = 0; i < 300; i++) {
            CaptchaGenerationResult result = generator.generate();
            String[] parts = result.displayText().split(" ");
            if ("/".equals(parts[1])) {
                hitDivide = true;
                int dividend = Integer.parseInt(parts[0]);
                int divisor = Integer.parseInt(parts[2]);
                assertThat(dividend % divisor).isZero();
                assertThat(dividend / divisor).isEqualTo(Integer.parseInt(result.answer()));
            }
        }
        assertThat(hitDivide).as("300 次生成应至少命中一次除法分支").isTrue();
    }

    /**
     * 答案正确（不区分大小写）且验证码未过期时，校验应通过，并立即删除 Redis 记录。
     */
    @Test
    void verify_shouldReturnTrue_whenCharacterAnswerMatchesIgnoringCase() {
        seedPayload("cap-1", CaptchaType.CHARACTER, "AbC9");

        boolean result = imageCaptchaService.verify("cap-1", "abc9");

        assertThat(result).isTrue();
        assertThat(store).doesNotContainKey(key("cap-1"));
    }

    /**
     * 答案错误时，校验应不通过，且该验证码 id 对应的记录同样被立即删除。
     */
    @Test
    void verify_shouldReturnFalse_whenAnswerMismatch() {
        seedPayload("cap-2", CaptchaType.CHARACTER, "AbC9");

        boolean result = imageCaptchaService.verify("cap-2", "wrong");

        assertThat(result).isFalse();
        assertThat(store).doesNotContainKey(key("cap-2"));
    }

    /**
     * 验证码 id 不存在（未生成、已过期、已被消费）时，校验应不通过，不抛出未分类异常。
     */
    @Test
    void verify_shouldReturnFalse_whenCaptchaIdNotExist() {
        boolean result = imageCaptchaService.verify("missing-id", "anything");

        assertThat(result).isFalse();
    }

    /**
     * 同一验证码 id 第一次校验通过后，第二次校验（无论提交什么答案）应因该 id 已被消费而
     * 返回不通过（一次性）。
     */
    @Test
    void verify_shouldBeOneTimeUse() {
        seedPayload("cap-3", CaptchaType.CHARACTER, "Z9k2");

        assertThat(imageCaptchaService.verify("cap-3", "Z9k2")).isTrue();
        assertThat(imageCaptchaService.verify("cap-3", "Z9k2")).isFalse();
    }

    /**
     * 算式模式下应按数值精确匹配答案。
     */
    @Test
    void verify_shouldMatchArithmeticAnswerNumerically() {
        seedPayload("cap-4", CaptchaType.ARITHMETIC, "10");

        assertThat(imageCaptchaService.verify("cap-4", "10")).isTrue();
    }

    /**
     * 算式模式下答案数值不相等时，校验应不通过。
     */
    @Test
    void verify_shouldRejectArithmeticAnswerMismatch() {
        seedPayload("cap-5", CaptchaType.ARITHMETIC, "10");

        assertThat(imageCaptchaService.verify("cap-5", "11")).isFalse();
    }

    /**
     * 直接向内存 Redis 存储写入一条已知的验证码答案记录，供校验相关用例精确控制初始状态。
     *
     * @param captchaId 验证码 id
     * @param type      验证码类型
     * @param answer    正确答案
     */
    private void seedPayload(String captchaId, CaptchaType type, String answer) {
        store.put(key(captchaId), JacksonUtils.toJson(new CaptchaAnswerPayload(type, answer)));
    }

    /**
     * 读取指定验证码 id 当前存储的答案载荷（不删除），供生成相关用例断言内容。
     *
     * @param captchaId 验证码 id
     * @return 存储的答案载荷
     */
    private CaptchaAnswerPayload readPayload(String captchaId) {
        return JacksonUtils.toObj(store.get(key(captchaId)), CaptchaAnswerPayload.class);
    }

    /**
     * 拼接验证码 Redis key，与 {@code ImageCaptchaServiceImpl} 内部使用的前缀保持一致。
     *
     * @param captchaId 验证码 id
     * @return 完整 Redis key
     */
    private String key(String captchaId) {
        return "captcha:image:" + captchaId;
    }
}
