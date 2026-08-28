package cn.nihility.rbac.sync.openapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 增量游标拉取返回的一条变更指针，只返回定位信息，不返回业务数据；详情需要调用方另行
 * {@code GET /open/api/sync/pull?ids=} 复核（app-sync-changelog-pull change design.md
 * Decision 4/11）。{@code eventId}/{@code entityId}/{@code entityVersion}/{@code changeSeq}
 * 均为 {@code BIGINT}，对外按十进制字符串序列化，避免 JavaScript 数字精度超过
 * {@code 2^53-1} 后丢失精度。
 */
@Getter
@Builder
@Schema(description = "增量游标拉取变更指针")
public class SyncChangePointerVO {

    /** 雪花算法生成的全局事件标识，十进制字符串，用于幂等追踪，不参与排序。 */
    @Schema(description = "雪花全局事件标识，十进制字符串，用于幂等追踪，不参与排序")
    private final String eventId;

    /** 数据类型：ORG/USER/POSITION/APP/ROLE。 */
    @Schema(description = "数据类型：ORG/USER/POSITION/APP/ROLE")
    private final String entityType;

    /** 被变更实体主键 id，十进制字符串。 */
    @Schema(description = "被变更实体主键 id，十进制字符串")
    private final String entityId;

    /** 操作类型：CREATE/UPDATE/ENABLE/DISABLE/DELETE。 */
    @Schema(description = "操作类型：CREATE/UPDATE/ENABLE/DISABLE/DELETE")
    private final String operationType;

    /** 本次变更完成后的实体结果版本，十进制字符串。 */
    @Schema(description = "本次变更完成后的实体结果版本，十进制字符串")
    private final String entityVersion;

    /** 数据库自增游标，十进制字符串，用于排序续传。 */
    @Schema(description = "数据库自增游标，十进制字符串，用于排序续传")
    private final String changeSeq;

    /** 变更发生时间。 */
    @Schema(description = "变更发生时间")
    private final LocalDateTime changeTime;
}
