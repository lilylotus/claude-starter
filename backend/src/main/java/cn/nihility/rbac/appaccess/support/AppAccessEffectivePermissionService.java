package cn.nihility.rbac.appaccess.support;

import cn.nihility.rbac.appaccess.dto.AppAccessEffectiveItemVO;
import cn.nihility.rbac.common.result.PageResult;

/**
 * 最终生效权限计算与查询业务逻辑接口（spec.md"最终生效权限计算规则"/"最终生效权限查询"
 * 需求）：合并判定逻辑（{@code DENY} 例外 &gt; {@code GRANT} 例外 &gt; 启用中策略产生的
 * 授权记录 &gt; 不可访问）封装成唯一实现，供管理端查询接口
 * （{@code AppAccessQueryController}）与 SSO 登录拦截
 * （{@code cn.nihility.rbac.sso.support.AppAccessAuthorizationChecker}）唯一共用，
 * 不允许调用方各自拼 SQL（design.md Risks 缓解措施）。提供"仅身份维度"与"考虑请求上下文"
 * 两种判定粒度（app-access-request-control change design.md Decision 3）：前者不看
 * 策略是否配置了请求控制条件，供管理端审计查询使用；后者额外校验命中策略的浏览器/IP
 * 白名单是否被当前请求满足，供 SSO 登录拦截使用。
 */
public interface AppAccessEffectivePermissionService {

    /**
     * 判定指定用户对指定应用是否具备最终生效授权（仅身份维度，不考虑请求上下文）。
     *
     * @param userId 用户 id
     * @param appId  应用 id
     * @return {@code true} 表示可访问
     */
    boolean isAuthorized(Long userId, Long appId);

    /**
     * 判定指定用户对指定应用是否具备最终生效授权（考虑请求上下文）：在仅身份维度判定的
     * 基础上，对"启用中策略产生的授权记录"这一来源追加请求控制约束——命中的多条策略中，
     * 只要存在至少一条策略的请求控制条件（浏览器白名单/IP 白名单）被当前请求满足即判定为
     * 可访问；{@code GRANT} 人工例外不受此约束（app-access-request-control change
     * design.md Decision 3）。
     *
     * @param userId    用户 id
     * @param appId     应用 id
     * @param clientIp  当前请求的客户端 IP，取不到时传 {@code null}（视为不命中任何 IP
     *                  白名单条目）
     * @param userAgent 当前请求的 {@code User-Agent}，取不到时传 {@code null}（视为不命中
     *                  任何浏览器白名单条目）
     * @return {@code true} 表示可访问
     */
    boolean isAuthorized(Long userId, Long appId, String clientIp, String userAgent);

    /**
     * 分页查询指定用户当前可访问的全部应用，标明每条结果的判定依据。
     *
     * @param userId         用户 id
     * @param includeRevoked 是否额外返回被 {@code DENY} 例外收回、但本应命中策略的应用
     *                       （标记为 {@code REVOKED}），供审计查看
     * @param page           页码，从 1 开始
     * @param pageSize       每页条数
     * @return 分页结果
     */
    PageResult<AppAccessEffectiveItemVO> listEffectiveByUser(Long userId, boolean includeRevoked, int page,
            int pageSize);

    /**
     * 分页查询指定应用当前具备最终生效权限的全部用户，标明每条结果的判定依据。
     *
     * @param appId    应用 id
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<AppAccessEffectiveItemVO> listEffectiveByApp(Long appId, int page, int pageSize);
}
