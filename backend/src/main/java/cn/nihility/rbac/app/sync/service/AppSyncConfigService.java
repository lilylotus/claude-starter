package cn.nihility.rbac.app.sync.service;

import cn.nihility.rbac.app.sync.dto.AppSyncDomainConfigUpdateRequest;
import cn.nihility.rbac.app.sync.dto.AppSyncDomainConfigVO;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingSaveRequest;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingVO;
import java.util.List;

/**
 * 应用同步配置业务逻辑接口：负责组织/用户/应用/角色/字典 5 个数据域启用开关+拉取分页
 * 大小的默认生成、查询、修改，以及组织/用户/应用/角色 4 个数据域字段级同步映射的查询、
 * 整体替换保存（app-sync-field-mapping change proposal.md）。
 */
public interface AppSyncConfigService {

    /**
     * 为一个刚创建的应用生成默认的 5 行数据域配置（组织/用户/应用/角色/字典各一行，默认
     * 全部不启用，分页大小默认 20），供 {@code AppConfigServiceImpl#createDefaultConfig}
     * 在同一事务内调用。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @param operator 操作人（当前登录用户 id 文本），用于填充审计字段
     */
    void createDefaultDomainConfigs(Long appRefId, String operator);

    /**
     * 查询指定应用的 5 行数据域配置。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @return 数据域配置视图对象列表
     */
    List<AppSyncDomainConfigVO> listDomainConfigs(Long appRefId);

    /**
     * 修改指定应用某个数据域的启用开关与拉取分页大小。
     *
     * @param appRefId   应用 id（{@code tab_app.id}）
     * @param syncDomain 数据域，必须是 {@code SyncDomain} 五个常量之一
     * @param request    数据域配置修改请求
     * @return 修改后的数据域配置视图对象
     */
    AppSyncDomainConfigVO updateDomainConfig(Long appRefId, String syncDomain,
            AppSyncDomainConfigUpdateRequest request);

    /**
     * 查询指定应用某个数据域的字段映射列表，{@code syncDomain=DICT} 时直接返回空列表
     * （字典数据域不支持字段级同步配置）。
     *
     * @param appRefId   应用 id（{@code tab_app.id}）
     * @param syncDomain 数据域
     * @return 字段映射视图对象列表
     */
    List<AppSyncFieldMappingVO> listFieldMappings(Long appRefId, String syncDomain);

    /**
     * 整体替换指定应用某个数据域的字段映射列表：先按 {@code (appRefId, syncDomain)} 物理
     * 删除既有映射行，再按提交内容批量插入，整个操作在一个事务内完成，不做按行 diff。
     *
     * @param appRefId   应用 id（{@code tab_app.id}）
     * @param syncDomain 数据域，必须落在 {@code SyncDomain.FIELD_MAPPING_DOMAINS} 内
     * @param requests   本次提交的完整字段映射行列表
     * @return 保存后的字段映射视图对象列表
     */
    List<AppSyncFieldMappingVO> replaceFieldMappings(Long appRefId, String syncDomain,
            List<AppSyncFieldMappingSaveRequest> requests);
}
