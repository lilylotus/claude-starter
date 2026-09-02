package cn.nihility.rbac.sso.sms.service.impl;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.RedisUtils;
import cn.nihility.rbac.sso.config.RbacSmsProperties;
import cn.nihility.rbac.sso.sms.SmsSender;
import cn.nihility.rbac.sso.sms.service.SmsCodeService;
import cn.nihility.rbac.sso.support.SsoMobileUserResolver;
import cn.nihility.rbac.user.entity.UserEntity;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link SmsCodeService} 的默认实现（add-sso-login-methods change design.md Decision 4）。
 */
@Service
@RequiredArgsConstructor
public class SmsCodeServiceImpl implements SmsCodeService {

    /** 验证码 Redis key 前缀，完整 key 为该前缀 + 手机号。 */
    private static final String CODE_KEY_PREFIX = "sso:sms:code:";

    /** 发送冷却 Redis key 前缀，完整 key 为该前缀 + 手机号。 */
    private static final String COOLDOWN_KEY_PREFIX = "sso:sms:cooldown:";

    /** 每日发送计数 Redis key 前缀，完整 key 为该前缀 + 手机号 + {@code :yyyyMMdd}。 */
    private static final String DAILY_KEY_PREFIX = "sso:sms:daily:";

    /** 连续校验失败计数 Redis key 前缀，完整 key 为该前缀 + 手机号。 */
    private static final String ATTEMPTS_KEY_PREFIX = "sso:sms:attempts:";

    /** 每日计数 key 的日期后缀格式。 */
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 验证码位数。 */
    private static final int CODE_DIGITS = 6;

    /** 短信验证码相关配置。 */
    private final RbacSmsProperties smsProperties;

    /** 短信发送可插拔接口。 */
    private final SmsSender smsSender;

    /** 手机号唯一匹配用户解析组件。 */
    private final SsoMobileUserResolver ssoMobileUserResolver;

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendCode(String mobile) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + mobile;
        if (Boolean.TRUE.equals(RedisUtils.hasKey(cooldownKey))) {
            throw new BusinessException("请求过于频繁，请稍后再试");
        }
        RedisUtils.set(cooldownKey, "1", smsProperties.getCooldownSeconds(), TimeUnit.SECONDS);

        String dailyKey = DAILY_KEY_PREFIX + mobile + ":" + LocalDate.now().format(DAY_FORMATTER);
        long secondsUntilMidnight = Math.max(1,
                Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay()).getSeconds());
        Long dailyCount = RedisUtils.increment(dailyKey, secondsUntilMidnight, TimeUnit.SECONDS);
        if (dailyCount != null && dailyCount > smsProperties.getDailyLimit()) {
            throw new BusinessException("今日验证码发送次数已达上限，请明天再试");
        }

        Optional<UserEntity> userOpt = ssoMobileUserResolver.resolveUniqueEnabledUser(mobile);
        if (userOpt.isEmpty()) {
            // 防枚举：0 条或多条命中均静默跳过真正发送，接口整体仍视为成功（design.md Decision 4）。
            return;
        }
        String code = generateCode();
        RedisUtils.set(CODE_KEY_PREFIX + mobile, code, smsProperties.getCodeExpireSeconds(), TimeUnit.SECONDS);
        RedisUtils.delete(ATTEMPTS_KEY_PREFIX + mobile);
        smsSender.send(mobile, code);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifyCode(String mobile, String code) {
        String codeKey = CODE_KEY_PREFIX + mobile;
        String attemptsKey = ATTEMPTS_KEY_PREFIX + mobile;

        Optional<String> storedCode = RedisUtils.get(codeKey);
        if (storedCode.isPresent() && storedCode.get().equals(code)) {
            RedisUtils.delete(codeKey);
            RedisUtils.delete(attemptsKey);
            return true;
        }

        Long attempts = RedisUtils.increment(attemptsKey, smsProperties.getCodeExpireSeconds(), TimeUnit.SECONDS);
        if (attempts != null && attempts >= smsProperties.getMaxAttempts()) {
            // 达到连续失败上限：当前验证码立即失效，要求重新获取（design.md Decision 4）。
            RedisUtils.delete(codeKey);
            RedisUtils.delete(attemptsKey);
        }
        return false;
    }

    /**
     * 生成一个 6 位数字验证码，允许前导 0。
     *
     * @return 验证码明文
     */
    private String generateCode() {
        int bound = (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", ThreadLocalRandom.current().nextInt(0, bound));
    }
}
