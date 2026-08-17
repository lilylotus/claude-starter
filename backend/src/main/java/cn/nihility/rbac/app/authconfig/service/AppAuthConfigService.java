package cn.nihility.rbac.app.authconfig.service;

import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigUpdateRequest;
import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigVO;

/**
 * 应用单点登录协议配置业务逻辑接口（app-auth-protocol-config change proposal.md）。
 */
public interface AppAuthConfigService {

    /**
     * 新建应用时创建默认认证配置：协议类型"无"，两个匹配列表均为空，与创建默认对外接口
     * 凭证配置在同一事务内完成（由 {@code AppConfigServiceImpl#createDefaultConfig} 调用）。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @param operator 操作人
     */
    void createDefaultConfig(Long appRefId, String operator);

    /**
     * 查询应用当前的认证配置，含按该应用 AppId 计算出的 6 个只读协议接口地址。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @return 应用单点登录协议配置视图对象
     */
    AppAuthConfigVO getByAppId(Long appRefId);

    /**
     * 修改应用的认证配置：协议类型与对应的匹配列表。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @param request  修改请求
     * @return 修改后的应用单点登录协议配置视图对象
     */
    AppAuthConfigVO updateConfig(Long appRefId, AppAuthConfigUpdateRequest request);
}
