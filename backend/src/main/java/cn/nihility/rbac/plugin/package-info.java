/**
 * 插件包升级模块（plugin-jar-upgrade change）：在主程序自身组件扫描完成之后、任何 Bean
 * 开始实例化之前，扫描 {@code plugins/} 目录下的 jar 包，将其中标注
 * {@code @Controller}/{@code @RestController}/{@code @Service}/{@code @Component}/
 * {@code @Configuration} 的类解析为 Bean 定义注册进主 Spring 容器，并支持插件类通过
 * {@link cn.nihility.rbac.plugin.annotation.PluginOverride} 显式覆盖主程序中的同类型 Bean。
 * <p>
 * 分层说明（见 openspec/changes/plugin-jar-upgrade/design.md）：
 * <ul>
 *     <li>{@code annotation}——插件覆盖声明注解 {@code @PluginOverride}；</li>
 *     <li>{@code config}——插件目录、覆盖黑名单等配置项绑定；</li>
 *     <li>{@code support}——插件发现、类加载隔离、组件扫描、Bean 定义注册与失败隔离、
 *     内存态插件状态登记（{@code PluginRegistry}）等核心机制；</li>
 *     <li>{@code dto}——插件状态查询接口对外的数据传输对象；</li>
 *     <li>{@code service}/{@code controller}——插件（Bean 定义注册阶段）状态查询管理接口。</li>
 * </ul>
 * 本模块不提供运行期重新扫描/加载能力，新增或更新插件需要重启应用（design.md Non-Goals）。
 * <p>
 * <b>插件 jar 编写规范（部署/开发速查）</b>：
 * <ol>
 *     <li>把插件 jar 放入应用工作目录下的 {@code plugins/} 目录（路径可通过
 *     {@code rbac.plugin.directory} 配置项调整），<b>重启应用</b>后生效——不支持运行期新增/
 *     更新插件免重启加载，也不提供重新扫描接口；</li>
 *     <li>插件 jar 内标注 {@code @Controller}/{@code @RestController}/{@code @Service}/
 *     {@code @Component}/{@code @Configuration} 的类会被识别为 Bean 注册进主容器；</li>
 *     <li>需要覆盖主程序已有 Bean 时，在插件类上显式标注
 *     {@code @PluginOverride(target = 主程序类.class)}（如
 *     {@code @PluginOverride(target = cn.nihility.rbac.xxx.service.XxxService.class)}），
 *     未标注该注解的同名/同接口类一律作为独立 Bean 处理，不做任何隐式覆盖；</li>
 *     <li>可选在插件 jar 内提供 {@code META-INF/plugin.properties}（键
 *     {@code name}/{@code version}/{@code priority}，均可选），如：
 *     <pre>{@code
 *     name=my-demo-plugin
 *     version=1.0.0
 *     priority=10
 *     }</pre>
 *     {@code priority} 数值越大越晚处理，多个插件覆盖同一目标时后处理者生效；缺省该文件或
 *     其中某个键时用来源文件名/{@code unknown}/{@code 0} 兜底；</li>
 *     <li>{@code rbac.plugin.override.deny-list} 配置项（默认含认证过滤器、全局异常处理器、
 *     全局响应包装器等安全关键类全限定名）命中的目标类禁止被覆盖，命中时该插件类的 Bean
 *     定义直接不注册，插件其余非受限部分正常处理，不影响插件整体状态；</li>
 *     <li><b>插件发布前必须自行完成最小化启动自测</b>：插件 Bean 若在构造器/
 *     {@code @PostConstruct} 等实例化阶段抛出异常，会导致主程序本次启动整体失败——这是本模块
 *     明确选择的取舍（换取原生、干净的 Bean 覆盖语义），不属于"Bean 定义注册阶段"的隔离范围，
 *     不会被 {@code GET /api/v1/plugins} 查询到"部分失败"的中间态，需要看主程序启动日志定位
 *     到具体插件、具体类。</li>
 * </ol>
 */
package cn.nihility.rbac.plugin;
