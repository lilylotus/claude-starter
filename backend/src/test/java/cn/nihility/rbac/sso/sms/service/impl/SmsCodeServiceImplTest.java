package cn.nihility.rbac.sso.sms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sso.sms.service.SmsCodeService;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link SmsCodeServiceImpl} 的测试，起真实 MySQL/Redis 连接（同项目既有"不做重量级 mock"的
 * 测试风格），覆盖短信验证码防枚举（0 条/1 条/多条命中）、发送冷却与每日上限限流、验证码
 * 校验成功/失败与连续失败上限使验证码失效（add-sso-login-methods change design.md
 * Decision 4）。
 */
@SpringBootTest
class SmsCodeServiceImplTest {

    /** 被测服务，真实注入。 */
    @Autowired
    private SmsCodeService smsCodeService;

    /** 用户数据访问接口，用于插入测试手机号对应的用户。 */
    @Autowired
    private UserMapper userMapper;

    /** 字符串 Redis 模板，用于测试内直接操作/清理 Redis。 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 本用例插入的测试用户 id，供 {@link #tearDown()} 清理。 */
    private final List<Long> insertedUserIds = new ArrayList<>();

    /** 本用例使用过的手机号，供 {@link #tearDown()} 清理 Redis key。 */
    private final List<String> usedMobiles = new ArrayList<>();

    /**
     * 每个用例结束后清理插入的测试用户与产生的 Redis key。
     */
    @AfterEach
    void tearDown() {
        insertedUserIds.forEach(userMapper::deleteById);
        for (String mobile : usedMobiles) {
            stringRedisTemplate.delete(Set.of("sso:sms:code:" + mobile, "sso:sms:cooldown:" + mobile,
                    "sso:sms:attempts:" + mobile));
            stringRedisTemplate.keys("sso:sms:daily:" + mobile + ":*").forEach(stringRedisTemplate::delete);
        }
    }

    /**
     * 手机号唯一匹配一个启用状态用户时，发送验证码应真正生成并写入 Redis。
     */
    @Test
    void sendCode_shouldGenerateCode_whenMobileMatchesUniqueEnabledUser() {
        String mobile = seedUser(UserStatus.ENABLED);

        smsCodeService.sendCode(mobile);

        assertThat(stringRedisTemplate.opsForValue().get("sso:sms:code:" + mobile)).isNotBlank();
    }

    /**
     * 手机号未匹配任何用户（0 条）时，发送验证码不应报错，但也不应真正生成验证码
     * （design.md Decision 4"防枚举优先"）。
     */
    @Test
    void sendCode_shouldNotGenerateCode_whenMobileNotMatched() {
        String mobile = randomMobile();
        usedMobiles.add(mobile);

        smsCodeService.sendCode(mobile);

        assertThat(stringRedisTemplate.opsForValue().get("sso:sms:code:" + mobile)).isNull();
    }

    /**
     * 手机号匹配多个启用状态用户（一号多绑定）时，发送验证码同样不应真正生成验证码。
     */
    @Test
    void sendCode_shouldNotGenerateCode_whenMobileMatchesMultipleUsers() {
        String mobile = randomMobile();
        usedMobiles.add(mobile);
        insertUser(mobile, UserStatus.ENABLED);
        insertUser(mobile, UserStatus.ENABLED);

        smsCodeService.sendCode(mobile);

        assertThat(stringRedisTemplate.opsForValue().get("sso:sms:code:" + mobile)).isNull();
    }

    /**
     * 手机号匹配一个已停用用户时，视同未匹配，不应真正生成验证码。
     */
    @Test
    void sendCode_shouldNotGenerateCode_whenMatchedUserDisabled() {
        String mobile = seedUser(UserStatus.DISABLED);

        smsCodeService.sendCode(mobile);

        assertThat(stringRedisTemplate.opsForValue().get("sso:sms:code:" + mobile)).isNull();
    }

    /**
     * 冷却时间内重复发送应被拒绝。
     */
    @Test
    void sendCode_shouldReject_whenWithinCooldown() {
        String mobile = randomMobile();
        usedMobiles.add(mobile);

        smsCodeService.sendCode(mobile);

        assertThatThrownBy(() -> smsCodeService.sendCode(mobile))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请求过于频繁");
    }

    /**
     * 超过每日发送上限（默认 10 次）时应拒绝，绕开冷却限制以隔离验证每日上限本身
     * （每次调用后手动清除冷却 key 模拟冷却窗口已过去）。
     */
    @Test
    void sendCode_shouldReject_whenDailyLimitExceeded() {
        String mobile = randomMobile();
        usedMobiles.add(mobile);

        for (int i = 0; i < 10; i++) {
            smsCodeService.sendCode(mobile);
            stringRedisTemplate.delete("sso:sms:cooldown:" + mobile);
        }

        assertThatThrownBy(() -> smsCodeService.sendCode(mobile))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已达上限");
    }

    /**
     * 提交正确验证码时应校验通过，并清除验证码与失败计数（防止复用）。
     */
    @Test
    void verifyCode_shouldSucceed_andClearState_whenCodeMatches() {
        String mobile = seedUser(UserStatus.ENABLED);
        smsCodeService.sendCode(mobile);
        String code = stringRedisTemplate.opsForValue().get("sso:sms:code:" + mobile);

        boolean result = smsCodeService.verifyCode(mobile, code);

        assertThat(result).isTrue();
        assertThat(stringRedisTemplate.opsForValue().get("sso:sms:code:" + mobile)).isNull();
    }

    /**
     * 提交错误验证码时应校验失败。
     */
    @Test
    void verifyCode_shouldFail_whenCodeMismatch() {
        String mobile = seedUser(UserStatus.ENABLED);
        smsCodeService.sendCode(mobile);

        boolean result = smsCodeService.verifyCode(mobile, "000000");

        assertThat(result).isFalse();
    }

    /**
     * 验证码不存在（未发送过/已过期）时应校验失败，而不是抛出异常。
     */
    @Test
    void verifyCode_shouldFail_whenCodeNotExist() {
        String mobile = randomMobile();
        usedMobiles.add(mobile);

        assertThat(smsCodeService.verifyCode(mobile, "123456")).isFalse();
    }

    /**
     * 连续校验失败达到上限（默认 5 次）后，验证码应立即失效，即使后续提交的是原本正确的验证码
     * 也应校验失败。
     */
    @Test
    void verifyCode_shouldInvalidateCode_afterMaxAttemptsReached() {
        String mobile = seedUser(UserStatus.ENABLED);
        smsCodeService.sendCode(mobile);
        String code = stringRedisTemplate.opsForValue().get("sso:sms:code:" + mobile);

        for (int i = 0; i < 5; i++) {
            assertThat(smsCodeService.verifyCode(mobile, "000000")).isFalse();
        }

        assertThat(smsCodeService.verifyCode(mobile, code)).isFalse();
    }

    /**
     * 插入一个指定状态的测试用户并返回其手机号，同时登记到清理列表。
     *
     * @param status 用户状态
     * @return 测试手机号
     */
    private String seedUser(int status) {
        String mobile = randomMobile();
        usedMobiles.add(mobile);
        insertUser(mobile, status);
        return mobile;
    }

    /**
     * 插入一个指定手机号、指定状态的测试用户。
     *
     * @param mobile 手机号
     * @param status 用户状态
     */
    private void insertUser(String mobile, int status) {
        LocalDateTime now = LocalDateTime.now();
        UserEntity user = UserEntity.builder()
                .name("短信登录测试用户")
                .code("sms-test-" + UUID.randomUUID())
                .gender("unknown")
                .mobile(mobile)
                .showOrder(0)
                .status(status)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        userMapper.insert(user);
        insertedUserIds.add(user.getId());
    }

    /**
     * 生成一个符合手机号格式（{@code 1} 开头共 11 位数字）的随机测试手机号，避免不同用例
     * /并行测试之间互相污染 Redis 计数状态。
     *
     * @return 随机手机号
     */
    private String randomMobile() {
        StringBuilder sb = new StringBuilder("1");
        for (int i = 0; i < 10; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(0, 10));
        }
        return sb.toString();
    }
}
