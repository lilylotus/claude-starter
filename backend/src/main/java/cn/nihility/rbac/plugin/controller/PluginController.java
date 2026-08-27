package cn.nihility.rbac.plugin.controller;

import cn.nihility.rbac.plugin.dto.PluginListVO;
import cn.nihility.rbac.plugin.service.PluginQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 插件管理接口：查询已发现插件的（Bean 定义注册阶段）状态、失败原因、覆盖关系列表
 * （plugin-jar-management capability spec "插件状态查询"）。只读，不提供运行期重新扫描/
 * 加载接口——新增或更新插件需要重启应用（design.md Non-Goals）。管理员权限校验复用项目
 * 既有的 {@code identity-token}/{@code menu} 请求头机制（{@code IdentityAuthFilter} +
 * {@code AuthorizationService}），本接口对应权限编码 {@code PluginManagement:plugin:view}
 * （见 权限资源.txt），不需要在本类内重复写权限判断代码。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "插件管理", description = "插件（Bean 定义注册阶段）状态查询接口，只读")
public class PluginController {

    /** 插件状态查询业务逻辑接口。 */
    private final PluginQueryService pluginQueryService;

    /**
     * 查询全部已发现插件的名称、来源文件、Bean 定义注册阶段状态、失败原因、覆盖关系列表。
     *
     * @return 插件列表查询响应
     */
    @Operation(summary = "查询插件列表", description = "返回每个已发现插件的名称、来源文件、Bean 定义注册阶段状态（REGISTERED/FAILED）、"
            + "失败原因、覆盖关系列表，以及跨插件的覆盖冲突记录；状态只反映定义注册阶段结果，"
            + "不反映后续 Bean 实例化阶段——若实例化阶段失败，主程序本次启动直接失败，需查看启动日志定位")
    @GetMapping("/api/v1/plugins")
    public PluginListVO list() {
        return pluginQueryService.list();
    }
}
