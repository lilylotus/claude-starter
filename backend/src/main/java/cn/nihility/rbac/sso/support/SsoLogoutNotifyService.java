package cn.nihility.rbac.sso.support;

import cn.nihility.rbac.app.authconfig.constant.AuthProtocol;
import cn.nihility.rbac.app.authconfig.dto.AppProtocolInfo;
import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.common.util.HttpClientUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.common.util.ThreadPoolUtils;
import cn.nihility.rbac.sso.session.SsoSessionAppCredential;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.sync.sign.NotifySignatureAppender;
import cn.nihility.rbac.sync.sign.SignCanonicalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 单点登出后端回调通知业务逻辑（add-sso-single-logout change design.md Decision 2）：登出时
 * 读取本次 {@code sso_session} 会话在每个应用最后一次签发的 CAS 服务票据/OAuth2 access token，
 * 对已配置了登出通知回调地址（{@code logoutNotifyUrl}）的应用逐一以 {@code POST} +
 * {@code application/x-www-form-urlencoded} 方式发起回调（CAS 应用回传表单字段 {@code ticket}，
 * OAuth2 应用回传表单字段 {@code access_token}），复用 {@link NotifySignatureAppender} 计算
 * 签名；通过 {@link ThreadPoolUtils} 并发提交、fire-and-forget，不等待 HTTP 响应返回，单个
 * 应用通知失败被独立捕获，不影响其余应用及登出主流程（同 {@code AppNotifyServiceImpl} 的
 * "单个应用异常不向外传播"既有定位，但本类不落审计表，见 design.md Non-Goals）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoLogoutNotifyService {

    /** 登出通知请求响应超时（毫秒），与 {@code AppNotifyServiceImpl} 保持一致。 */
    private static final long NOTIFY_RESPONSE_TIMEOUT_MILLIS = 3000L;

    /** CAS 协议回调表单字段名：服务票据。 */
    private static final String FORM_FIELD_TICKET = "ticket";

    /** OAuth2.0 协议回调表单字段名：access token。 */
    private static final String FORM_FIELD_ACCESS_TOKEN = "access_token";

    /** SSO 浏览器会话业务逻辑接口，读取/清理会话-应用凭证映射。 */
    private final SsoSessionService ssoSessionService;

    /** 应用协议校验入口，查询已启用单点登录协议的应用列表（登出通知回调地址、签名参数）。 */
    private final AppProtocolGuard appProtocolGuard;

    /** 出站通知请求签名参数构造工具。 */
    private final NotifySignatureAppender notifySignatureAppender;

    /** 应用对外接口凭证相关配置，提供 SM4 解密主密钥。 */
    private final AppSecretProperties appSecretProperties;

    /**
     * 触发一次单点登出后端回调通知：对本次会话实际登录过、且配置了登出通知回调地址的每个
     * 应用逐一发起（并发）回调；未登录过任何应用、或没有任何目标应用配置了回调地址时，
     * 本方法安静地不做任何事。读取完会话-应用凭证映射后立即清理该映射（design.md Decision 1
     * "登出时一并清理"），不等待通知任务的 HTTP 响应返回。
     *
     * @param ssoSessionToken 本次登出的 SSO 会话令牌
     */
    public void notifyLogout(String ssoSessionToken) {
        if (!StringUtils.hasText(ssoSessionToken)) {
            return;
        }
        Map<String, SsoSessionAppCredential> credentials = ssoSessionService.listAppCredentials(ssoSessionToken);
        ssoSessionService.clearAppCredentials(ssoSessionToken);
        if (credentials.isEmpty()) {
            return;
        }

        Map<String, AppProtocolInfo> activeApps = appProtocolGuard.listActiveProtocolApps().stream()
                .collect(Collectors.toMap(AppProtocolInfo::getAppId, Function.identity(), (first, second) -> first));
        credentials.forEach((appId, credential) -> submitNotifyTask(appId, credential, activeApps.get(appId)));
    }

    /**
     * 按目标应用当前配置决定是否提交一次通知任务：应用当前已不再启用单点登录协议、或未配置
     * 登出通知回调地址时跳过。提交本身可能因线程池饱和抛出 {@link RejectedExecutionException}，
     * 就地捕获并记录 WARN 日志，不影响登出主流程（tasks.md 3.4）。
     *
     * @param appId      应用对外标识
     * @param credential 该应用在本次会话最后一次签发的凭证
     * @param appInfo    该应用当前的协议配置，可能为 {@code null}（协议已被关闭/应用已不存在）
     */
    private void submitNotifyTask(String appId, SsoSessionAppCredential credential, AppProtocolInfo appInfo) {
        if (appInfo == null || !StringUtils.hasText(appInfo.getLogoutNotifyUrl())) {
            return;
        }
        try {
            ThreadPoolUtils.submit(() -> notifyOneApp(appInfo, credential));
        } catch (RejectedExecutionException e) {
            log.warn("提交应用[{}]登出通知任务被拒绝：线程池与队列均已饱和", appId, e);
        }
    }

    /**
     * 向单个应用发起一次登出回调通知请求，异常在方法内部吞掉并记录 WARN 日志，不向外抛出
     * （本方法运行在 {@link ThreadPoolUtils} 的工作线程内，异常无人捕获会被线程默认处理器
     * 打印为未捕获异常，仍不应影响其他任务）。
     *
     * @param appInfo    目标应用的协议配置与签名参数
     * @param credential 本次会话在该应用最后一次签发的凭证
     */
    private void notifyOneApp(AppProtocolInfo appInfo, SsoSessionAppCredential credential) {
        try {
            String formFieldName = AuthProtocol.CAS.equals(credential.protocol()) ? FORM_FIELD_TICKET : FORM_FIELD_ACCESS_TOKEN;
            Map<String, String> formFields = new LinkedHashMap<>();
            formFields.put(formFieldName, credential.credential());
            String requestBody = SignCanonicalizer.canonicalize(formFields);

            String secretKey = Sm4JdkUtils.decrypt(appInfo.getSecretKey(), appSecretProperties.getSm4Key());
            Map<String, String> headers = notifySignatureAppender.buildSignatureHeaders(
                    Boolean.TRUE.equals(appInfo.getNeedSign()), appInfo.getSignAlgorithm(), appInfo.getAccessKey(),
                    secretKey, requestBody);

            HttpClientUtils.postForm(appInfo.getLogoutNotifyUrl(), headers, formFields, NOTIFY_RESPONSE_TIMEOUT_MILLIS);
        } catch (Exception e) {
            log.warn("向应用[{}]发送登出通知失败", appInfo.getAppId(), e);
        }
    }
}
