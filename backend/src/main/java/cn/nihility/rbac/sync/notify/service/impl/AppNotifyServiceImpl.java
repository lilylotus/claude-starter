package cn.nihility.rbac.sync.notify.service.impl;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.common.util.HttpClientUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.sync.notify.dto.NotifyAttemptOutcome;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.service.AppNotifyService;
import cn.nihility.rbac.sync.sign.NotifySignatureAppender;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 应用通知实际发送业务逻辑实现：只负责发起一次 HTTP 请求并把结果归类为
 * 成功/可重试失败/不可重试失败，不做状态机流转（由 {@code NotifySendCoordinator} 负责，
 * app-sync-changelog-pull change design.md Decision 6）。
 */
@Service
@RequiredArgsConstructor
public class AppNotifyServiceImpl implements AppNotifyService {

    /** 通知请求响应超时（毫秒），比全局默认更短，避免拖慢发送线程池太久（design.md Risks）。 */
    private static final long NOTIFY_RESPONSE_TIMEOUT_MILLIS = 3000L;

    /** {@code tab_app_notify_record.error_msg} 列的长度上限。 */
    private static final int ERROR_MSG_MAX_LENGTH = 500;

    /** HTTP 状态码 408（请求超时），归类为可重试。 */
    private static final int HTTP_REQUEST_TIMEOUT = 408;

    /** HTTP 状态码 429（请求过多），归类为可重试。 */
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    /** 应用对外接口凭证配置数据访问接口，按 {@code appRefId} 查询目标应用当前的签名配置。 */
    private final AppConfigMapper appConfigMapper;

    /** 出站通知请求签名参数构造工具。 */
    private final NotifySignatureAppender notifySignatureAppender;

    /** 应用对外接口凭证相关配置，提供 SM4 解密主密钥。 */
    private final AppSecretProperties appSecretProperties;

    /**
     * {@inheritDoc}
     */
    @Override
    public NotifyAttemptOutcome sendOnce(AppNotifyRecordEntity task) {
        AppConfigEntity target = appConfigMapper.selectOne(
                new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAppRefId, task.getAppRefId()));
        if (target == null) {
            // 目标应用的对外接口配置在任务落库之后被删除，属于不可重试场景。
            return NotifyAttemptOutcome.dead(null, "目标应用配置不存在或已被删除");
        }

        try {
            String secretKey = Sm4JdkUtils.decrypt(target.getSecretKey(), appSecretProperties.getSm4Key());
            Map<String, String> headers = notifySignatureAppender.buildSignatureHeaders(
                    Boolean.TRUE.equals(target.getNeedSign()), target.getSignAlgorithm(), target.getAccessKey(),
                    secretKey, task.getRequestBody());

            HttpClientUtils.HttpResult result = HttpClientUtils.postBinary(task.getNotifyUrl(), headers,
                    task.getRequestBody().getBytes(StandardCharsets.UTF_8), "application/json;charset=UTF-8",
                    NOTIFY_RESPONSE_TIMEOUT_MILLIS);

            int httpStatus = result.getStatusCode();
            if (httpStatus >= 200 && httpStatus < 300) {
                return NotifyAttemptOutcome.success(httpStatus);
            }
            String errorMsg = truncate("通知回调返回非成功状态码：" + httpStatus, ERROR_MSG_MAX_LENGTH);
            return isRetryableHttpStatus(httpStatus) ? NotifyAttemptOutcome.retry(httpStatus, errorMsg)
                    : NotifyAttemptOutcome.dead(httpStatus, errorMsg);
        } catch (Exception e) {
            // 网络异常（连接失败、超时等）一律归类为可重试（design.md Decision 6）。
            String errorMsg = truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    ERROR_MSG_MAX_LENGTH);
            return NotifyAttemptOutcome.retry(null, errorMsg);
        }
    }

    /**
     * 判断一个非 2xx 的 HTTP 状态码是否属于可重试类型：408/429/5xx 可重试，其他 4xx 不可重试。
     *
     * @param httpStatus 外部接口返回的 HTTP 状态码
     * @return 是否可重试
     */
    private boolean isRetryableHttpStatus(int httpStatus) {
        return httpStatus == HTTP_REQUEST_TIMEOUT || httpStatus == HTTP_TOO_MANY_REQUESTS || httpStatus >= 500;
    }

    /**
     * 把文本截断到给定长度上限，防止超出对应数据库列的长度限制。
     *
     * @param text      原始文本
     * @param maxLength 长度上限
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
