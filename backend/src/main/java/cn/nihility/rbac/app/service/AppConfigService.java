package cn.nihility.rbac.app.service;

import cn.nihility.rbac.app.dto.AppConfigVO;
import cn.nihility.rbac.app.dto.SecretKeyVO;
import cn.nihility.rbac.app.dto.SignAlgorithmUpdateRequest;
import cn.nihility.rbac.app.dto.SyncConfigUpdateRequest;

/**
 * 应用对外接口配置业务逻辑接口：负责默认凭证配置的生成、查询、签名算法/同步范围的修改、
 * SecretKey 的重置（app-api-credentials-config change proposal.md）。
 */
public interface AppConfigService {

    /**
     * 为一个刚创建的应用生成默认的对外接口配置（AppId/AccessKey/SecretKey 凭证三元组、
     * 默认签名算法、默认全关闭的同步开关），供 {@code AppServiceImpl#create} 在同一事务内
     * 调用。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @param operator 操作人（当前登录用户 id 文本），用于填充审计字段
     */
    void createDefaultConfig(Long appRefId, String operator);

    /**
     * 查询指定应用的对外接口配置详情，不做管辖组织范围校验（对齐现有
     * {@code AppServiceImpl#getById} 的既有行为，详情类查询不受管辖范围限制）。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @return 应用对外接口配置视图对象
     */
    AppConfigVO getByAppId(Long appRefId);

    /**
     * 修改指定应用的接口签名算法。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @param request  签名算法修改请求
     * @return 修改后的应用对外接口配置视图对象
     */
    AppConfigVO updateSignAlgorithm(Long appRefId, SignAlgorithmUpdateRequest request);

    /**
     * 修改指定应用的数据同步范围开关。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @param request  同步配置修改请求
     * @return 修改后的应用对外接口配置视图对象
     */
    AppConfigVO updateSyncConfig(Long appRefId, SyncConfigUpdateRequest request);

    /**
     * 重置指定应用的 SecretKey，生成一个新值替换当前值，响应体单次返回明文。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @return 携带新 SecretKey 明文的响应对象
     */
    SecretKeyVO resetSecretKey(Long appRefId);
}
