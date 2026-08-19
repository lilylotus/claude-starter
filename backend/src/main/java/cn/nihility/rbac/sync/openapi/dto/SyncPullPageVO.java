package cn.nihility.rbac.sync.openapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * 分页拉取接口返回的整页视图对象（app-sync-drop-changelog change design.md Decision 2
 * 修订版）：顶层携带本次请求的数据类型与本次实际生效的分页参数，{@code records} 里每条元素
 * 直接是经字段映射转换后的业务字段 Map，额外合并了 {@code bizId}/{@code bizCode}/
 * {@code bizStatus}/{@code updateTime} 四个通用固定键，不再是"元信息+data"的嵌套结构，也不在
 * 每条记录里重复 {@code dataType}。其中 {@code bizStatus} 直接取自业务表原始状态字段，
 * 不经过、也不受字段映射配置影响——如果该数据域配置了字段映射，转换后的输出可能只包含已映射
 * 字段（管理员未显式映射 {@code status} 时不会包含原始状态字段），{@code bizStatus} 保证
 * 无论是否配置字段映射，调用方都能稳定地判断记录当前是启用/停用/已删除
 * （app-sync-drop-changelog change design.md Decision 1）。此外还有几个仅对特定数据域生效的
 * 领域特定固定键：{@code userCode}/{@code orgCode}（仅任职数据类型的记录出现，分别是该任职
 * 记录关联用户/组织的业务编码）、{@code dictTypeCode}（仅字典数据类型的记录出现）——与四个
 * 通用固定键不同，这些键在不相关的数据域记录里完全不出现（不是"键存在值为 null"，而是键本身
 * 不存在）（design.md Decision 7/8，五次实现后修正）。
 * 顶层还携带 {@code dataSize}（本页 {@code records} 实际条数）与 {@code latestUpdateTime}
 * （本页记录中最大的更新时间），供调用方无需自己遍历 {@code records} 就能判断本页条数、并把
 * {@code latestUpdateTime} 原样作为下一次增量拉取的 {@code updateTimeFrom}
 * （design.md Decision 9，四次实现后修正）。
 */
@Getter
@Builder
@Schema(description = "分页拉取结果整页视图对象")
public class SyncPullPageVO {

    /** 数据类型：ORG/USER/POSITION/APP/ROLE/DICT，整页只出现一次。 */
    @Schema(description = "数据类型：ORG/USER/POSITION/APP/ROLE/DICT")
    private final String dataType;

    /** 本次实际使用的页码。 */
    @Schema(description = "本次实际使用的页码")
    private final Integer page;

    /** 本次实际使用的每页大小（调用方显式传入值或回退后的默认值）。 */
    @Schema(description = "本次实际使用的每页大小（调用方显式传入值或回退后的默认值）")
    private final Integer pageSize;

    /** 本页 {@code records} 的实际条数，与 {@code pageSize}（本次请求的每页大小上限）是两个不同概念。 */
    @Schema(description = "本页 records 的实际条数（最后一页不满页时小于 pageSize）")
    private final Integer dataSize;

    /**
     * 本页记录中最大的更新时间，{@code records} 为空（翻到最后一页/未开通该数据类型/同步
     * 总开关关闭等场景）时为 {@code null}；调用方可将其原样作为下一次增量拉取请求的
     * {@code updateTimeFrom}，不需要自己遍历 {@code records} 求最大值。
     */
    @Schema(description = "本页记录中最大的更新时间，records 为空时为 null，可作为下一次增量拉取 updateTimeFrom 的取值")
    private final LocalDateTime latestUpdateTime;

    /**
     * 本页数据列表，每条元素是按字段映射转换后的业务字段 Map，额外合并了
     * {@code bizId}（记录主键 id）、{@code bizCode}（业务编码，任职数据类型或未配置业务编码
     * 字段时为 {@code null}，键仍保留）、{@code bizStatus}（记录当前状态，取值即业务表原始
     * 状态字段，不受字段映射配置影响，六个数据域均恒有值）、{@code updateTime}（该记录当前
     * 更新时间）四个通用固定键；任职数据类型的记录额外合并 {@code userCode}（关联用户的业务
     * 编码）与 {@code orgCode}（关联组织的业务编码），字典数据类型的记录额外合并
     * {@code dictTypeCode}（所属字典类型的编码），这些键仅在对应数据域的记录中出现。
     */
    @Schema(description = "本页数据列表，每条元素已合并 bizId/bizCode/bizStatus/updateTime 四个通用固定键；"
            + "任职记录额外合并 userCode/orgCode，字典记录额外合并 dictTypeCode")
    private final List<Map<String, Object>> records;
}
