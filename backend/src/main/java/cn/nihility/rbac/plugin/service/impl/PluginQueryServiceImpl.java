package cn.nihility.rbac.plugin.service.impl;

import cn.nihility.rbac.plugin.dto.PluginBeanOverrideVO;
import cn.nihility.rbac.plugin.dto.PluginListVO;
import cn.nihility.rbac.plugin.dto.PluginOverrideConflictVO;
import cn.nihility.rbac.plugin.dto.PluginSkippedClassVO;
import cn.nihility.rbac.plugin.dto.PluginVO;
import cn.nihility.rbac.plugin.service.PluginQueryService;
import cn.nihility.rbac.plugin.support.PluginBeanOverride;
import cn.nihility.rbac.plugin.support.PluginInfo;
import cn.nihility.rbac.plugin.support.PluginOverrideConflict;
import cn.nihility.rbac.plugin.support.PluginRegistry;
import cn.nihility.rbac.plugin.support.PluginSkippedClass;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 插件（Bean 定义注册阶段）状态查询业务逻辑实现：直接读取
 * {@code PluginBeanDefinitionRegistrar} 在应用启动阶段写入的内存态 {@link PluginRegistry}
 * 并转换为对外 DTO，不做任何写操作（design.md Decision 6：v1 不提供运行期重新扫描/加载能力）。
 */
@Service
@RequiredArgsConstructor
public class PluginQueryServiceImpl implements PluginQueryService {

    /** 内存态插件登记表，由 {@code PluginBeanDefinitionRegistrar} 在容器启动阶段注册为单例 Bean。 */
    private final PluginRegistry pluginRegistry;

    /**
     * {@inheritDoc}
     */
    @Override
    public PluginListVO list() {
        List<PluginVO> plugins = pluginRegistry.getPlugins().stream().map(this::toVO).toList();
        List<PluginOverrideConflictVO> conflicts = pluginRegistry.getOverrideConflicts().stream().map(this::toVO).toList();
        return PluginListVO.builder().plugins(plugins).overrideConflicts(conflicts).build();
    }

    /**
     * 转换插件状态记录为视图对象。
     *
     * @param pluginInfo 插件状态记录
     * @return 视图对象
     */
    private PluginVO toVO(PluginInfo pluginInfo) {
        return PluginVO.builder()
                .name(pluginInfo.getName())
                .fileName(pluginInfo.getFileName())
                .version(pluginInfo.getVersion())
                .priority(pluginInfo.getPriority())
                .status(pluginInfo.getStatus().name())
                .failureReason(pluginInfo.getFailureReason())
                .overrides(pluginInfo.getOverrides().stream().map(this::toVO).toList())
                .skippedClasses(pluginInfo.getSkippedClasses().stream().map(this::toVO).toList())
                .registeredBeanNames(pluginInfo.getRegisteredBeanNames())
                .build();
    }

    /**
     * 转换覆盖记录为视图对象。
     *
     * @param override 覆盖记录
     * @return 视图对象
     */
    private PluginBeanOverrideVO toVO(PluginBeanOverride override) {
        return PluginBeanOverrideVO.builder()
                .pluginClassName(override.pluginClassName())
                .targetClassName(override.targetClassName())
                .beanName(override.beanName())
                .build();
    }

    /**
     * 转换跳过记录为视图对象。
     *
     * @param skipped 跳过记录
     * @return 视图对象
     */
    private PluginSkippedClassVO toVO(PluginSkippedClass skipped) {
        return PluginSkippedClassVO.builder().className(skipped.className()).reason(skipped.reason()).build();
    }

    /**
     * 转换覆盖冲突记录为视图对象。
     *
     * @param conflict 覆盖冲突记录
     * @return 视图对象
     */
    private PluginOverrideConflictVO toVO(PluginOverrideConflict conflict) {
        return PluginOverrideConflictVO.builder()
                .targetClassName(conflict.targetClassName())
                .previousPluginName(conflict.previousPluginName())
                .winningPluginName(conflict.winningPluginName())
                .beanName(conflict.beanName())
                .build();
    }
}
