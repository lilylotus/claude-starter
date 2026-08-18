package cn.nihility.rbac.app.authconfig.service;

import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigUpdateRequest;
import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigVO;
import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingSaveRequest;
import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingVO;
import java.util.List;

/**
 * 应用单点登录协议配置业务逻辑接口（app-auth-protocol-config change proposal.md），另含
 * 用户信息响应字段映射的查询、整体替换保存（add-sso-userinfo-field-mapping change
 * proposal.md）。
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

    /**
     * 查询指定应用的用户信息响应字段映射列表（CAS/OAuth2.0 协议共用）。该应用在
     * {@code tab_app_userinfo_field_mapping} 无任何记录时，返回现算的默认两行（"用户ID"、
     * "姓名"），不触发任何写库操作（design.md Decision 4）。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @return 用户信息响应字段映射视图对象列表
     */
    List<AppUserinfoFieldMappingVO> listUserinfoFieldMappings(Long appRefId);

    /**
     * 整体替换指定应用的用户信息响应字段映射列表：先按 {@code appRefId} 物理删除既有映射行，
     * 再按提交内容批量插入，整个操作在一个事务内完成，不做按行 diff。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @param requests 本次提交的完整字段映射行列表
     * @return 保存后的用户信息响应字段映射视图对象列表
     */
    List<AppUserinfoFieldMappingVO> replaceUserinfoFieldMappings(Long appRefId,
            List<AppUserinfoFieldMappingSaveRequest> requests);
}
