package cn.nihility.rbac.sync.notify.service.impl;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.constant.SyncMode;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.common.util.HttpClientUtils;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.constant.SyncOperationType;
import cn.nihility.rbac.sync.notify.constant.NotifyStatus;
import cn.nihility.rbac.sync.notify.dto.NotifyPayload;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.mapper.AppNotifyRecordMapper;
import cn.nihility.rbac.sync.notify.service.AppNotifyService;
import cn.nihility.rbac.sync.sign.NotifySignatureAppender;
import cn.nihility.rbac.sync.sign.SignConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 应用通知发送业务逻辑实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppNotifyServiceImpl implements AppNotifyService {

    /** 通知请求响应超时（毫秒），比全局默认更短，避免拖慢 Disruptor 消费者线程太久（design.md Risks）。 */
    private static final long NOTIFY_RESPONSE_TIMEOUT_MILLIS = 3000L;

    /** 通知/落库场景下审计字段的固定操作人标识：变更事件消费发生在后台线程，无登录用户上下文。 */
    private static final String SYSTEM_OPERATOR = "system";

    /** 应用对外接口凭证配置数据访问接口，按 {@code appRefId} 查询目标应用当前的同步方式与通知配置。 */
    private final AppConfigMapper appConfigMapper;

    /** 应用通知发送记录数据访问接口。 */
    private final AppNotifyRecordMapper appNotifyRecordMapper;

    /** 出站通知请求签名参数构造工具。 */
    private final NotifySignatureAppender notifySignatureAppender;

    /** 应用对外接口凭证相关配置，提供 SM4 解密主密钥。 */
    private final AppSecretProperties appSecretProperties;

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyIfConfigured(AppDataChangeLogEntity changeLog) {
        AppConfigEntity target = appConfigMapper.selectOne(new LambdaQueryWrapper<AppConfigEntity>()
                .eq(AppConfigEntity::getAppRefId, changeLog.getAppRefId()));
        if (target == null || !SyncMode.NOTIFY.equals(target.getSyncMode())) {
            return;
        }

        try {
            notifyOneApp(changeLog, target);
        } catch (Exception e) {
            // 单个应用通知异常不应影响处理领域变更事件的调用方（app-sync-notify-pull
            // spec"一个应用通知失败不影响其他应用"场景），此处兜底捕获，notifyOneApp 内部
            // 已经把网络异常转换为失败记录，这里只是防御性兜底，避免遗漏的运行时异常向外传播。
            log.warn("向应用[{}]发送变更通知时发生未预期异常", target.getAppId(), e);
        }
    }

    /**
     * 向单个应用发起一次通知请求，并把结果写入 {@code tab_app_notify_record}。
     *
     * @param changeLog 变更记录
     * @param target    目标应用对外接口凭证配置
     */
    private void notifyOneApp(AppDataChangeLogEntity changeLog, AppConfigEntity target) {
        NotifyPayload payload = NotifyPayload.builder()
                .sequence(changeLog.getId())
                .dataType(changeLog.getDataType())
                .operationType(SyncOperationType.code(changeLog.getOperationType()))
                .bizId(changeLog.getBizId())
                .occurredAt(changeLog.getCreateTime())
                .extra(parseNotifyParams(target.getNotifyParams()))
                .build();
        String requestBody = JacksonUtils.toJson(payload);

        Integer httpStatus = null;
        String errorMsg = null;
        int notifyStatus;
        try {
            String secretKey = Sm4JdkUtils.decrypt(target.getSecretKey(), appSecretProperties.getSm4Key());
            String url = notifySignatureAppender.appendSignatureIfNeeded(target.getNotifyUrl(),
                    Boolean.TRUE.equals(target.getNeedSign()), target.getSignAlgorithm(), target.getAccessKey(),
                    secretKey, requestBody);

            Map<String, String> headers = new HashMap<>();
            headers.put(SignConstants.HEADER_APP_KEY, target.getAccessKey());
            HttpClientUtils.HttpResult result = HttpClientUtils.postBinary(url, headers,
                    requestBody.getBytes(StandardCharsets.UTF_8), "application/json;charset=UTF-8",
                    NOTIFY_RESPONSE_TIMEOUT_MILLIS);

            httpStatus = result.getStatusCode();
            notifyStatus = httpStatus >= 200 && httpStatus < 300 ? NotifyStatus.SUCCESS : NotifyStatus.FAILURE;
            if (notifyStatus == NotifyStatus.FAILURE) {
                errorMsg = truncate("通知回调返回非成功状态码：" + httpStatus);
            }
        } catch (Exception e) {
            notifyStatus = NotifyStatus.FAILURE;
            errorMsg = truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }

        saveNotifyRecord(changeLog.getId(), target.getAppRefId(), notifyStatus, httpStatus, errorMsg);
    }

    /**
     * 把通知发送结果写入 {@code tab_app_notify_record}。
     *
     * @param changeLogId  关联的变更记录 id
     * @param appRefId     应用 id
     * @param notifyStatus 通知状态
     * @param httpStatus   外部接口返回的 HTTP 状态码，失败且未收到响应时为 {@code null}
     * @param errorMsg     失败原因摘要，成功时为 {@code null}
     */
    private void saveNotifyRecord(Long changeLogId, Long appRefId, int notifyStatus, Integer httpStatus,
            String errorMsg) {
        LocalDateTime now = LocalDateTime.now();
        AppNotifyRecordEntity record = AppNotifyRecordEntity.builder()
                .changeLogId(changeLogId)
                .appRefId(appRefId)
                .notifyStatus(notifyStatus)
                .httpStatus(httpStatus)
                .errorMsg(errorMsg)
                .createBy(SYSTEM_OPERATOR)
                .createTime(now)
                .updateBy(SYSTEM_OPERATOR)
                .updateTime(now)
                .build();
        appNotifyRecordMapper.insert(record);
    }

    /**
     * 把应用配置的通知自定义参数原始 JSON 文本解析为 {@code Map<String,String>}。
     *
     * @param notifyParams 原始 JSON 文本，可能为空
     * @return 解析后的自定义参数，解析失败或为空时返回空 Map
     */
    private Map<String, String> parseNotifyParams(String notifyParams) {
        if (!StringUtils.hasText(notifyParams)) {
            return Map.of();
        }
        try {
            return JacksonUtils.toObj(notifyParams, JacksonUtils.MAP_STRING_TYPE_REFERENCE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 把失败原因摘要截断到 {@code tab_app_notify_record.error_msg} 列的长度上限（500）。
     *
     * @param message 原始失败原因
     * @return 截断后的失败原因
     */
    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
