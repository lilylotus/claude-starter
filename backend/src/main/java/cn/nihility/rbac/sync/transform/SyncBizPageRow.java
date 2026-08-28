package cn.nihility.rbac.sync.transform;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * 分页拉取业务表当前数据的单条查询结果行（app-sync-drop-changelog change design.md
 * Decision 2），由 {@link SyncBizPageQueryResolver} 产出，供
 * {@code SyncPullServiceImpl} 组装最终返回视图对象。
 */
@Getter
@Builder
public class SyncBizPageRow {

    /** 业务主键 id。 */
    private final Long id;

    /** 业务编码，POSITION 数据域没有该字段恒为 {@code null}。 */
    private final String code;

    /** 当前更新时间。 */
    private final LocalDateTime updateTime;

    /** 当前状态（2000 启用/3000 停用/-1000 已删除），供 {@code bizStatus} 固定键取值，不受字段映射配置影响。 */
    private final Integer status;

    /**
     * 面向外部同步消费者的实体结果版本，仅 ORG/USER/POSITION/APP/ROLE 五个数据域有值，
     * DICT 数据域恒为 {@code null}（app-sync-changelog-pull change design.md Decision 5，
     * 字典不是五类版本化同步实体之一）；供 {@code version} 固定键取值，输出前转换为十进制
     * 字符串（design.md Decision 11）。
     */
    private final Long version;

    /**
     * 该行关联用户的业务编码，仅 POSITION 数据域使用，其余数据域恒为 {@code null}，
     * 供 {@code userCode} 领域特定固定键取值（app-sync-drop-changelog change design.md
     * Decision 8，三次实现后修正）。
     */
    private final String userCode;

    /**
     * 该行关联组织的业务编码，仅 POSITION 数据域使用，其余数据域恒为 {@code null}，
     * 供 {@code orgCode} 领域特定固定键取值（app-sync-drop-changelog change design.md
     * Decision 8，五次实现后修正）。
     */
    private final String orgCode;

    /**
     * 该行所属字典类型的编码，仅 DICT 数据域使用，其余数据域恒为 {@code null}，供
     * {@code dictTypeCode} 领域特定固定键取值（app-sync-drop-changelog change design.md
     * Decision 7，三次实现后修正）。
     */
    private final String dictTypeCode;

    /** 该行当前的字段快照 Map（key 为实体属性名），供字段映射转换使用。 */
    private final Map<String, Object> data;
}
