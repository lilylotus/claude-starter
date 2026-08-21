package cn.nihility.rbac.ssoprotocollog.service.impl;

import cn.nihility.rbac.common.util.ClientRequestUtils;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogResult;
import cn.nihility.rbac.ssoprotocollog.entity.SsoProtocolLogEntity;
import cn.nihility.rbac.ssoprotocollog.mapper.SsoProtocolLogMapper;
import cn.nihility.rbac.ssoprotocollog.service.SsoProtocolLogRecorder;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link SsoProtocolLogRecorder} 的默认实现：通过 {@link RequestContextHolder} 获取当前
 * HTTP 请求解析客户端 IP/User-Agent，最终写入 {@code tab_sso_protocol_log}。客户端 IP
 * 解析逻辑复用共享工具 {@link ClientRequestUtils#resolveClientIp}，与 {@code LoginLogRecorderImpl}
 * 同构（add-sso-protocol-access-log change design.md Decision 2）。
 */
@Service
@RequiredArgsConstructor
public class SsoProtocolLogRecorderImpl implements SsoProtocolLogRecorder {

    /** {@code createBy}/{@code appId} 均为空时使用的兜底值。 */
    private static final String UNKNOWN_OPERATOR = "unknown";

    /** 携带浏览器/操作系统/设备类型信息的请求头。 */
    private static final String USER_AGENT_HEADER = "User-Agent";

    /** SSO 协议调用记录数据访问接口。 */
    private final SsoProtocolLogMapper ssoProtocolLogMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordSuccess(String protocol, String eventType, String appId, Long appRefId, Long userId,
            String sessionId) {
        record(SsoProtocolLogResult.SUCCESS, protocol, eventType, appId, appRefId, userId, sessionId, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordFailure(String protocol, String eventType, String appId, Long appRefId, Long userId,
            String sessionId, String failReason, Long deniedPolicyId) {
        record(SsoProtocolLogResult.FAILED, protocol, eventType, appId, appRefId, userId, sessionId, failReason,
                deniedPolicyId);
    }

    /**
     * 统一的落库入口：解析请求上下文信息、写入日志。
     *
     * @param result         调用结果
     * @param protocol       协议类型
     * @param eventType      事件类型
     * @param appId          原始 appId/client_id 参数值，可为 {@code null}
     * @param appRefId       解析出的应用 id，可为 {@code null}
     * @param userId         关联的用户 id，可为 {@code null}
     * @param sessionId      本次调用所属 SSO 会话标识，可为 {@code null}
     * @param failReason     失败原因文案，调用成功时为 {@code null}
     * @param deniedPolicyId 拒绝来源的策略 id，仅"应用访问授权策略拒绝"失败原因下非空
     */
    private void record(int result, String protocol, String eventType, String appId, Long appRefId, Long userId,
            String sessionId, String failReason, Long deniedPolicyId) {
        HttpServletRequest request = currentRequest();
        LocalDateTime now = LocalDateTime.now();
        String operator = StringUtils.hasText(appId) ? appId : UNKNOWN_OPERATOR;

        SsoProtocolLogEntity entity = SsoProtocolLogEntity.builder()
                .appRefId(appRefId)
                .appId(appId)
                .protocol(protocol)
                .eventType(eventType)
                .userId(userId)
                .sessionId(sessionId)
                .result(result)
                .failReason(failReason)
                .deniedPolicyId(deniedPolicyId)
                .clientIp(ClientRequestUtils.resolveClientIp(request))
                .userAgent(request == null ? null : request.getHeader(USER_AGENT_HEADER))
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();

        ssoProtocolLogMapper.insert(entity);
    }

    /**
     * 获取当前线程绑定的 HTTP 请求，非 HTTP 上下文（如单元测试）中调用时返回 {@code null}。
     *
     * @return 当前 HTTP 请求，取不到时为 {@code null}
     */
    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }
}
