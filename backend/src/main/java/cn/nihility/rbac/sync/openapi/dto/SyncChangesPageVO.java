package cn.nihility.rbac.sync.openapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 增量游标拉取变更指针的响应整体视图对象（app-sync-changelog-pull change design.md
 * Decision 4/9/10/11）。
 */
@Getter
@Builder
@Schema(description = "增量游标拉取变更指针响应")
public class SyncChangesPageVO {

    /** 数据类型：ORG/USER/POSITION/APP/ROLE。 */
    @Schema(description = "数据类型：ORG/USER/POSITION/APP/ROLE")
    private final String entityType;

    /** 本次请求实际使用的起始游标，十进制字符串。 */
    @Schema(description = "本次请求实际使用的起始游标，十进制字符串")
    private final String sinceSeq;

    /**
     * "本轮扫描到的最后一条底层流水"的游标，十进制字符串，供调用方作为下一次请求的
     * {@code sinceSeq}：即使 {@code records} 为空（候选记录均被组织范围过滤掉），只要本轮
     * 扫描过底层流水就会前进，不会原地不动；不代表调用方消费确认（design.md Decision
     * 4/9）。
     */
    @Schema(description = "本轮已扫描到的最后一条底层流水的游标，十进制字符串，作为下一次请求的 sinceSeq；即使 records "
            + "为空也可能前进，不代表消费确认")
    private final String nextSeq;

    /**
     * 底层变更流水是否还有未扫描的记录（不是"是否还有可见记录"）：{@code true} 时建议调用方
     * 携带 {@code nextSeq} 继续拉取下一页。
     */
    @Schema(description = "底层变更流水是否还有未扫描的记录（不是\"是否还有可见记录\"）")
    private final boolean hasMore;

    /**
     * 应用级同步配置纪元，任一同步配置（总开关/数据域开关/组织范围/字段映射）变化时原子
     * 递增；调用方发现该值变化后必须对该应用全部已启用数据域重新全量同步（design.md
     * Decision 10）。十进制字符串序列化。
     */
    @Schema(description = "应用级同步配置纪元，十进制字符串；变化后需对该应用全部已启用数据域重新全量同步")
    private final String configEpoch;

    /** 本页变更指针列表，只包含定位信息，不含业务数据。 */
    @Schema(description = "本页变更指针列表，只包含定位信息，不含业务数据")
    private final List<SyncChangePointerVO> records;
}
