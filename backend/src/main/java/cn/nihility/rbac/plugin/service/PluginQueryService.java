package cn.nihility.rbac.plugin.service;

import cn.nihility.rbac.plugin.dto.PluginListVO;

/**
 * 插件（Bean 定义注册阶段）状态查询业务逻辑接口（plugin-jar-management capability spec
 * "插件状态查询"）。
 */
public interface PluginQueryService {

    /**
     * 查询全部已发现插件的状态及覆盖冲突记录。
     *
     * @return 插件列表查询响应
     */
    PluginListVO list();
}
