package cn.nihility.rbac.identity.upstream.service;

import cn.nihility.rbac.identity.upstream.dto.UpstreamConnectionConfigRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamScheduleConfigRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSourceCreateRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSourceUpdateRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSourceVO;
import java.util.List;

/**
 * 上游数据源业务逻辑接口：负责数据源的增删改查、启用停用、连接配置与调度配置维护、
 * 手动触发同步（spec.md Requirement：上游数据源基础配置管理/接口方式连接配置/
 * 数据库表方式连接配置/定时调度触发/手动立即同步）。
 */
public interface UpstreamSourceService {

    /**
     * 查询全部上游数据源，按 {@code id} 升序返回。
     *
     * @return 上游数据源视图对象列表
     */
    List<UpstreamSourceVO> list();

    /**
     * 查询上游数据源详情。
     *
     * @param id 上游数据源 id
     * @return 上游数据源视图对象
     */
    UpstreamSourceVO getById(Long id);

    /**
     * 创建上游数据源，{@code enabled} 默认固定为 {@code false}，并在同一事务内为其生成
     * 组织/用户/任职 3 行默认数据域配置（均不启用）。
     *
     * @param request 创建请求
     * @return 创建后的上游数据源视图对象
     */
    UpstreamSourceVO create(UpstreamSourceCreateRequest request);

    /**
     * 更新上游数据源基础信息（名称、同步方式）。
     *
     * @param id      上游数据源 id
     * @param request 更新请求
     * @return 更新后的上游数据源视图对象
     */
    UpstreamSourceVO updateBasicInfo(Long id, UpstreamSourceUpdateRequest request);

    /**
     * 更新上游数据源连接配置，按数据源当前 {@code syncType} 校验并加密敏感字段落库。
     *
     * @param id      上游数据源 id
     * @param request 连接配置更新请求
     * @return 更新后的上游数据源视图对象
     */
    UpstreamSourceVO updateConnectionConfig(Long id, UpstreamConnectionConfigRequest request);

    /**
     * 更新上游数据源调度配置。
     *
     * @param id      上游数据源 id
     * @param request 调度配置更新请求
     * @return 更新后的上游数据源视图对象
     */
    UpstreamSourceVO updateScheduleConfig(Long id, UpstreamScheduleConfigRequest request);

    /**
     * 启用上游数据源。
     *
     * @param id 上游数据源 id
     * @return 更新后的上游数据源视图对象
     */
    UpstreamSourceVO enable(Long id);

    /**
     * 停用上游数据源。
     *
     * @param id 上游数据源 id
     * @return 更新后的上游数据源视图对象
     */
    UpstreamSourceVO disable(Long id);

    /**
     * 删除上游数据源，级联物理删除其下的数据域配置、字段映射配置与同步执行记录。
     *
     * @param id 上游数据源 id
     */
    void delete(Long id);

    /**
     * 手动触发一次同步，效果与定时到期自动触发一致，但不更新 {@code lastTriggerTime}
     * （不影响下次定时触发的判定基准）。
     *
     * @param id 上游数据源 id
     */
    void manualSync(Long id);
}
