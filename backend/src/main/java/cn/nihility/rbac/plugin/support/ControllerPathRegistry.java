package cn.nihility.rbac.plugin.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 已知请求路径登记表：非覆盖 Controller 的 Bean 定义注册前，与主程序自身及已处理插件的
 * 既有路径集合比对，冲突则拒绝该类的 Bean 定义注册（plugin-jar-management capability spec
 * "插件与已知路径/Bean 定义冲突"，design.md Decision 2）。覆盖场景（{@code @PluginOverride}
 * 声明覆盖某个已存在的 Controller）不受本登记表约束——同一 bean name 重新注册后最终只保留
 * 插件版本，{@code RequestMappingHandlerMapping} 不会出现重复映射。
 */
public class ControllerPathRegistry {

    /** 已登记路径 -&gt; 来源标识（主程序或某个插件名称），仅用于冲突提示。 */
    private final Map<String, String> pathOwners = new HashMap<>();

    /**
     * 尝试登记一批路径；只要其中任意一条已被占用即视为冲突，整批均不登记（保持"这一个类"
     * 要么全部路径生效、要么整体判定为冲突"的原子性，避免部分路径登记造成的不一致状态）。
     *
     * @param owner 来源标识（如 {@code "main"} 或插件名称），仅用于冲突提示文案
     * @param paths 待登记的路径集合
     * @return 冲突详情，路径未被占用时返回空
     */
    public Optional<String> tryRegister(String owner, Set<String> paths) {
        for (String path : paths) {
            String existingOwner = pathOwners.get(path);
            if (existingOwner != null) {
                return Optional.of("请求路径 [" + path + "] 已被 [" + existingOwner + "] 占用");
            }
        }
        for (String path : paths) {
            pathOwners.put(path, owner);
        }
        return Optional.empty();
    }
}
