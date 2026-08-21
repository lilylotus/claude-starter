package cn.nihility.rbac.sso.support;

import cn.nihility.rbac.app.authconfig.constant.AuthProtocol;
import cn.nihility.rbac.app.authconfig.dto.AppProtocolInfo;
import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import cn.nihility.rbac.app.authconfig.mapper.AppAuthConfigMapper;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.common.util.JacksonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

/**
 * CAS/OAuth2 协议校验入口（app-sso-protocol-runtime change design.md Decision 1）：按对外
 * 应用标识（AppId/client_id）查 {@link AppConfigEntity}/{@link AppAuthConfigEntity}，校验
 * 协议类型，并用 {@link AntPathMatcher} 校验 {@code service}/{@code redirect_uri} 是否落在
 * 已配置的匹配规则内。不经过 {@code AppAuthConfigService}/{@code AppConfigService}——那两个
 * 服务层带管辖组织范围校验/操作日志等管理端语义，这里是无登录态的公开端点，直接查 Mapper
 * 更清晰，同 {@code NotifySignatureAppender} 不经 Service 层直连 Mapper 的既有模式。
 */
@Component
@RequiredArgsConstructor
public class AppProtocolGuard {

    /** Ant 风格路径匹配器，用于 service/redirect_uri 白名单匹配。 */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 应用对外接口凭证配置数据访问接口。 */
    private final AppConfigMapper appConfigMapper;

    /** 应用单点登录协议配置数据访问接口。 */
    private final AppAuthConfigMapper appAuthConfigMapper;

    /**
     * 校验 {@code appId} 对应应用的协议类型为 CAS 且 {@code service} 匹配已配置的 ANT
     * 规则中的至少一条，均不满足时抛出 {@link SsoProtocolException}。
     *
     * @param appId   对外应用标识（{@code tab_app_config.open_app_id}）
     * @param service CAS {@code service} 参数
     * @return 校验通过的应用单点登录协议配置
     */
    public AppAuthConfigEntity assertCasServiceAllowed(String appId, String service) {
        AppAuthConfigEntity authConfig = resolveAuthConfig(appId);
        if (!AuthProtocol.CAS.equals(authConfig.getAuthProtocol())) {
            throw new SsoProtocolException("该应用未开启 CAS 单点登录协议");
        }
        if (!StringUtils.hasText(service)) {
            throw new SsoProtocolException("service 参数不能为空");
        }
        List<String> patterns = parsePatterns(authConfig.getServicePatterns());
        if (patterns.stream().noneMatch(pattern -> PATH_MATCHER.match(pattern, service))) {
            throw new SsoProtocolException("service 不匹配任何已配置的规则");
        }
        return authConfig;
    }

    /**
     * 校验 {@code clientId} 对应应用的协议类型为 OAuth2.0 且 {@code redirectUri} 匹配已配置
     * 的 ANT 规则中的至少一条，均不满足时抛出 {@link SsoProtocolException}。
     * {@code redirectUri} 为空时直接判不通过（design.md Decision 6"收紧为必填"）。
     *
     * @param clientId    对外应用标识（即 OAuth2 client_id）
     * @param redirectUri OAuth2 {@code redirect_uri} 参数
     * @return 校验通过的应用单点登录协议配置
     */
    public AppAuthConfigEntity assertOAuthRedirectUriAllowed(String clientId, String redirectUri) {
        AppAuthConfigEntity authConfig = resolveAuthConfig(clientId);
        if (!AuthProtocol.OAUTH2.equals(authConfig.getAuthProtocol())) {
            throw new SsoProtocolException("该应用未开启 OAuth2.0 单点登录协议");
        }
        if (!StringUtils.hasText(redirectUri)) {
            throw new SsoProtocolException("redirect_uri 参数不能为空");
        }
        List<String> patterns = parsePatterns(authConfig.getServicePatterns());
        if (patterns.stream().noneMatch(pattern -> PATH_MATCHER.match(pattern, redirectUri))) {
            throw new SsoProtocolException("redirect_uri 不匹配任何已配置的规则");
        }
        return authConfig;
    }

    /**
     * 校验 {@code appId} 对应应用存在且已开启单点登录协议（CAS 或 OAuth2.0），且
     * {@code service} 匹配已配置的回跳地址匹配列表（{@code servicePatterns}，CAS/OAuth2.0
     * 共用同一份存储）中的至少一条规则；应用不存在、协议类型为 {@code NONE} 或不匹配任何
     * 规则时均抛出 {@link SsoProtocolException} 拒绝（add-sso-single-logout change design.md
     * Decision 5，全局登出接口复用；unify-app-auth-service-patterns change 合并为统一读取
     * {@code servicePatterns}，不再按协议类型分支选列）。
     *
     * @param appId   对外应用标识
     * @param service 待校验的回跳地址（CAS 场景语义等同 service，OAuth2.0 场景语义等同
     *                redirect_uri）
     * @return 校验通过的应用单点登录协议配置
     */
    public AppAuthConfigEntity assertLogoutServiceAllowed(String appId, String service) {
        AppAuthConfigEntity authConfig = resolveAuthConfig(appId);
        if (!StringUtils.hasText(service)) {
            throw new SsoProtocolException("service 参数不能为空");
        }
        if (AuthProtocol.NONE.equals(authConfig.getAuthProtocol())) {
            throw new SsoProtocolException("该应用未开启单点登录协议");
        }
        List<String> patterns = parsePatterns(authConfig.getServicePatterns());
        if (patterns.stream().noneMatch(pattern -> PATH_MATCHER.match(pattern, service))) {
            throw new SsoProtocolException("service 不匹配任何已配置的规则");
        }
        return authConfig;
    }

    /**
     * 查询全部已启用单点登录协议（{@code authProtocol != NONE}）的应用，携带登出通知回调
     * 地址与对外接口凭证签名参数，供 {@link SsoLogoutNotifyService} 登出时按 {@code appId}
     * 匹配回调目标使用（add-sso-single-logout change tasks.md 3.1）。
     *
     * @return 已启用单点登录协议的应用列表
     */
    public List<AppProtocolInfo> listActiveProtocolApps() {
        return appAuthConfigMapper.selectActiveProtocolApps();
    }

    /**
     * 按对外应用标识（AppId/client_id）解析内部应用 id（{@code tab_app.id}），应用不存在时
     * 抛出 {@link SsoProtocolException}。供 {@code CasController}/{@code OAuthController}
     * 在票据验证/用户信息查询成功后解析出 {@code appRefId}，交给
     * {@code SsoUserinfoAttributesResolver} 查询用户信息字段映射配置
     * （add-sso-userinfo-field-mapping change tasks.md 4.2）。
     *
     * @param appId 对外应用标识
     * @return 应用 id（{@code tab_app.id}）
     */
    public Long resolveAppRefId(String appId) {
        AppConfigEntity appConfig = findAppConfig(appId);
        if (appConfig == null) {
            throw new SsoProtocolException("应用不存在");
        }
        return appConfig.getAppRefId();
    }

    /**
     * 按对外应用标识（AppId/client_id）尝试解析内部应用 id（{@code tab_app.id}），应用不存在
     * 时返回 {@code null} 而不是抛出异常。供 {@code SsoProtocolLogRecorder} 相关的日志记录场景
     * 在校验失败分支尽力填充 {@code appRefId}，不因解析不到应用而中断主流程
     * （add-sso-protocol-access-log change design.md Decision 4）。
     *
     * @param appId 对外应用标识
     * @return 应用 id（{@code tab_app.id}），解析不到应用时为 {@code null}
     */
    public Long resolveAppRefIdOrNull(String appId) {
        AppConfigEntity appConfig = findAppConfig(appId);
        return appConfig == null ? null : appConfig.getAppRefId();
    }

    /**
     * 按对外应用标识尝试解析应用的单点登录协议配置，应用不存在或未配置协议时返回空
     * {@link Optional} 而不是抛出异常。供全局单点登出接口在校验失败分支尽力还原该应用的
     * 实际协议类型用于日志记录，而不是笼统写死一个协议类型
     * （add-sso-protocol-access-log change design.md Decision 3）。
     *
     * @param appId 对外应用标识
     * @return 应用单点登录协议配置，解析不到时为空
     */
    public Optional<AppAuthConfigEntity> tryResolveAuthConfig(String appId) {
        try {
            return Optional.of(resolveAuthConfig(appId));
        } catch (SsoProtocolException e) {
            return Optional.empty();
        }
    }

    /**
     * 按对外应用标识查询该应用的对外接口凭证配置，供 OAuth2 {@code client_secret} 校验使用。
     * 应用不存在或协议类型不是 OAuth2.0 时抛出 {@link SsoProtocolException}，两种情况使用
     * 同一提示信息，不向调用方泄露具体区别（同 {@code OpenApiSignInterceptor} 既有先例）。
     *
     * @param clientId 对外应用标识（即 OAuth2 client_id）
     * @return 应用对外接口凭证配置
     */
    public AppConfigEntity resolveOAuthClientConfig(String clientId) {
        AppConfigEntity appConfig = findAppConfig(clientId);
        if (appConfig == null) {
            throw new SsoProtocolException("客户端不存在或未开启 OAuth2.0 单点登录协议");
        }
        AppAuthConfigEntity authConfig = appAuthConfigMapper.selectOne(
                new LambdaQueryWrapper<AppAuthConfigEntity>().eq(AppAuthConfigEntity::getAppRefId, appConfig.getAppRefId()));
        if (authConfig == null || !AuthProtocol.OAUTH2.equals(authConfig.getAuthProtocol())) {
            throw new SsoProtocolException("客户端不存在或未开启 OAuth2.0 单点登录协议");
        }
        return appConfig;
    }

    /**
     * 按对外应用标识解析该应用的单点登录协议配置，应用不存在时抛出 {@link SsoProtocolException}。
     *
     * @param appId 对外应用标识
     * @return 应用单点登录协议配置
     */
    private AppAuthConfigEntity resolveAuthConfig(String appId) {
        AppConfigEntity appConfig = findAppConfig(appId);
        if (appConfig == null) {
            throw new SsoProtocolException("应用不存在");
        }
        AppAuthConfigEntity authConfig = appAuthConfigMapper.selectOne(
                new LambdaQueryWrapper<AppAuthConfigEntity>().eq(AppAuthConfigEntity::getAppRefId, appConfig.getAppRefId()));
        if (authConfig == null) {
            throw new SsoProtocolException("应用未配置单点登录协议");
        }
        return authConfig;
    }

    /**
     * 按对外应用标识查询应用对外接口凭证配置。
     *
     * @param appId 对外应用标识
     * @return 应用对外接口凭证配置，不存在时返回 {@code null}
     */
    private AppConfigEntity findAppConfig(String appId) {
        if (!StringUtils.hasText(appId)) {
            return null;
        }
        return appConfigMapper.selectOne(new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAppId, appId));
    }

    /**
     * 解析匹配规则列表的 JSON 文本。
     *
     * @param patternsJson JSON 字符串数组文本，可能为空
     * @return 匹配规则列表，解析失败或为空时返回空列表
     */
    private List<String> parsePatterns(String patternsJson) {
        if (!StringUtils.hasText(patternsJson)) {
            return List.of();
        }
        List<String> patterns = JacksonUtils.toObj(patternsJson, JacksonUtils.LIST_STRING_TYPE_REFERENCE);
        return patterns == null ? List.of() : patterns.stream().filter(Objects::nonNull).toList();
    }
}
