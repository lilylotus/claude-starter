package cn.nihility.rbac.loginlog.service.impl;

import cn.nihility.rbac.common.util.ClientRequestUtils;
import cn.nihility.rbac.loginlog.constant.LoginResult;
import cn.nihility.rbac.loginlog.entity.LoginLogEntity;
import cn.nihility.rbac.loginlog.mapper.LoginLogMapper;
import cn.nihility.rbac.loginlog.service.LoginLogRecorder;
import cn.nihility.rbac.operationlog.util.UserAgentParser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link LoginLogRecorder} 的默认实现：通过 {@link RequestContextHolder} 获取当前
 * HTTP 请求解析登录 IP 与 User-Agent 相关信息（复用 {@code operationlog} 模块的
 * {@link UserAgentParser} 无状态工具类），最终写入 {@code tab_login_log}。客户端 IP
 * 解析逻辑已提炼为共享工具 {@link ClientRequestUtils#resolveClientIp}
 * （app-access-request-control change design.md Decision 6），本类不再维护私有实现，
 * 供该工具与 SSO 登录拦截请求控制校验共用同一份逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogRecorderImpl implements LoginLogRecorder {

    /** {@code createBy}/{@code loginAccount} 均为空时使用的兜底值。 */
    private static final String UNKNOWN_OPERATOR = "unknown";

    /** 携带浏览器/操作系统/设备类型信息的请求头。 */
    private static final String USER_AGENT_HEADER = "User-Agent";

    /** 登录日志数据访问接口。 */
    private final LoginLogMapper loginLogMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordSuccess(String loginAccount, Long userId, String userName) {
        record(LoginResult.SUCCESS, loginAccount, userId, userName, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordFailure(String loginAccount, Long userId, String userName, String failReason) {
        record(LoginResult.FAILED, loginAccount, userId, userName, failReason);
    }

    /**
     * 统一的落库入口：解析请求上下文信息、写入日志。
     *
     * @param loginResult  登录结果
     * @param loginAccount 本次登录尝试提交的账号，可为 {@code null}
     * @param userId       关联的用户 id，可为 {@code null}
     * @param userName     关联的用户姓名，可为 {@code null}
     * @param failReason   失败原因文案，登录成功时为 {@code null}
     */
    private void record(int loginResult, String loginAccount, Long userId, String userName, String failReason) {
        HttpServletRequest request = currentRequest();
        LocalDateTime now = LocalDateTime.now();
        String operator = StringUtils.hasText(loginAccount) ? loginAccount : UNKNOWN_OPERATOR;

        LoginLogEntity entity = LoginLogEntity.builder()
                .loginAccount(loginAccount)
                .userId(userId)
                .userName(userName)
                .loginResult(loginResult)
                .failReason(failReason)
                .loginIp(ClientRequestUtils.resolveClientIp(request))
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();
        fillUserAgentInfo(entity, request);

        loginLogMapper.insert(entity);
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

    /**
     * 读取 {@code User-Agent} 请求头并用 {@link UserAgentParser} 手写正则解析出登录终端
     * 类型/操作系统/浏览器，原样存入 {@code loginUserAgent}；请求不可用、请求头缺失或
     * 解析异常时相关字段均保持为 {@code null}，不影响日志主体写入。
     *
     * @param entity  待写入的登录日志实体
     * @param request 当前 HTTP 请求，可为 {@code null}
     */
    private void fillUserAgentInfo(LoginLogEntity entity, HttpServletRequest request) {
        if (request == null) {
            return;
        }
        String userAgentHeader = request.getHeader(USER_AGENT_HEADER);
        if (!StringUtils.hasText(userAgentHeader)) {
            return;
        }
        entity.setLoginUserAgent(userAgentHeader);

        try {
            entity.setLoginBrowser(UserAgentParser.parseBrowser(userAgentHeader));
            entity.setLoginOs(UserAgentParser.parseOs(userAgentHeader));
            entity.setLoginTerminal(UserAgentParser.parseTerminal(userAgentHeader));
        } catch (Exception e) {
            log.warn("解析 User-Agent 请求头失败：{}", userAgentHeader, e);
        }
    }
}
