package cn.nihility.rbac.sso.qrcode.service.impl;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.RedisUtils;
import cn.nihility.rbac.sso.config.RbacQrcodeProperties;
import cn.nihility.rbac.sso.qrcode.constant.QrcodeSessionStatus;
import cn.nihility.rbac.sso.qrcode.dto.QrcodeSessionPayload;
import cn.nihility.rbac.sso.qrcode.service.QrcodeSessionService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * {@link QrcodeSessionService} 的默认实现（add-sso-login-methods change design.md
 * Decision 5）：以 {@code sso:qrcode:<token>} 为 Redis key 存储 JSON 序列化的
 * {@link QrcodeSessionPayload}，延续项目"UUID 去横线做 opaque token"的既有模式
 * （同 {@code SsoSessionService}）。
 */
@Service
@RequiredArgsConstructor
public class QrcodeSessionServiceImpl implements QrcodeSessionService {

    /** Redis key 前缀，完整 key 为该前缀 + 会话令牌。 */
    private static final String KEY_PREFIX = "sso:qrcode:";

    /** 已消费状态收紧后的剩余有效期（秒），到期后自然过期，之后同 token 查询一律返回 EXPIRED。 */
    private static final long CONSUMED_TTL_SECONDS = 5;

    /** 二维码登录相关配置：会话有效期。 */
    private final RbacQrcodeProperties qrcodeProperties;

    /**
     * {@inheritDoc}
     */
    @Override
    public String create(String redirect, String appId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        QrcodeSessionPayload payload = QrcodeSessionPayload.builder()
                .status(QrcodeSessionStatus.PENDING)
                .appId(appId)
                .redirect(redirect)
                .consumed(false)
                .build();
        RedisUtils.setObject(key(token), payload, qrcodeProperties.getSessionExpireSeconds(), TimeUnit.SECONDS);
        return token;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<QrcodeSessionPayload> find(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return RedisUtils.getObject(key(token), QrcodeSessionPayload.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markScanned(String token) {
        find(token).ifPresent(payload -> {
            if (!QrcodeSessionStatus.PENDING.equals(payload.getStatus())) {
                return;
            }
            payload.setStatus(QrcodeSessionStatus.SCANNED);
            saveKeepingRemainingTtl(token, payload);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void confirm(String token, Long userId) {
        QrcodeSessionPayload payload = find(token)
                .orElseThrow(() -> new BusinessException("二维码已失效，请刷新后重试"));
        boolean confirmable = QrcodeSessionStatus.PENDING.equals(payload.getStatus())
                || QrcodeSessionStatus.SCANNED.equals(payload.getStatus());
        if (!confirmable) {
            throw new BusinessException("二维码已失效或已被使用");
        }
        payload.setStatus(QrcodeSessionStatus.CONFIRMED);
        payload.setUserId(userId);
        saveKeepingRemainingTtl(token, payload);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markConsumed(String token, QrcodeSessionPayload payload) {
        payload.setConsumed(true);
        RedisUtils.setObject(key(token), payload, CONSUMED_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 写回更新后的会话内容，尽量保留原 key 的剩余有效期（不因状态流转而重新拉满整段有效期），
     * 取不到剩余有效期（理论上不应发生，调用方已确认 key 存在）时兜底用完整会话有效期。
     *
     * @param token   会话令牌
     * @param payload 待写回的会话内容
     */
    private void saveKeepingRemainingTtl(String token, QrcodeSessionPayload payload) {
        Long remainingSeconds = RedisUtils.getExpire(key(token), TimeUnit.SECONDS);
        long ttl = remainingSeconds != null && remainingSeconds > 0
                ? remainingSeconds : qrcodeProperties.getSessionExpireSeconds();
        RedisUtils.setObject(key(token), payload, ttl, TimeUnit.SECONDS);
    }

    /**
     * 拼接会话的完整 Redis key。
     *
     * @param token 会话令牌
     * @return 完整 Redis key
     */
    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
