package cn.nihility.rbac.sync.openapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * 拉取接口返回的单条变更记录视图对象（app-sync-notify-pull-api change design.md
 * Decision 9）。
 */
@Getter
@Builder
@Schema(description = "变更记录")
public class SyncPullRecordVO {

    /** 序列号（{@code tab_app_data_change_log.id}）。 */
    @Schema(description = "序列号")
    private final Long sequence;

    /** 数据类型：ORG/USER/POSITION/APP/ROLE。 */
    @Schema(description = "数据类型：ORG/USER/POSITION/APP/ROLE")
    private final String dataType;

    /** 操作类型：CREATE/UPDATE/ENABLE/DISABLE/DELETE。 */
    @Schema(description = "操作类型：CREATE/UPDATE/ENABLE/DISABLE/DELETE")
    private final String operationType;

    /** 被变更对象 id。 */
    @Schema(description = "被变更对象 id")
    private final Long bizId;

    /** 变更发生时间。 */
    @Schema(description = "变更发生时间")
    private final LocalDateTime occurredAt;

    /**
     * 按字段映射转换后的字段数据（未配置字段映射时为完整原始字段快照），
     * key 为应用字段编码（或未转换时的原始实体属性名）。
     */
    @Schema(description = "字段数据，已按该应用的字段映射配置转换")
    private final Map<String, Object> data;
}
