package cn.nihility.rbac.identity.upstream.service;

import cn.nihility.rbac.identity.upstream.dto.UpstreamDomainConfigUpdateRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamDomainConfigVO;
import java.util.List;

/**
 * 上游数据源数据域配置业务逻辑接口：负责组织/用户/任职 3 个数据域配置行的默认生成、
 * 查询、修改。
 */
public interface UpstreamDomainConfigService {

    /**
     * 为一个刚创建的数据源生成默认的 3 行数据域配置（组织/用户/任职各一行，默认全部
     * 不启用），供 {@code UpstreamSourceServiceImpl#create} 在同一事务内调用。
     *
     * @param sourceId 上游数据源 id
     * @param operator 操作人（当前登录用户 id 文本），用于填充审计字段
     */
    void createDefaultDomainConfigs(Long sourceId, String operator);

    /**
     * 查询指定数据源的 3 行数据域配置，按固定顺序（组织→用户→任职）返回。
     *
     * @param sourceId 上游数据源 id
     * @return 数据域配置视图对象列表
     */
    List<UpstreamDomainConfigVO> listBySource(Long sourceId);

    /**
     * 修改指定数据源某个数据域的启用开关与取数来源配置。
     *
     * @param sourceId 上游数据源 id
     * @param dataType 数据域，必须是 {@code UpstreamDataType} 三个常量之一
     * @param request  数据域配置修改请求
     * @return 修改后的数据域配置视图对象
     */
    UpstreamDomainConfigVO update(Long sourceId, String dataType, UpstreamDomainConfigUpdateRequest request);
}
