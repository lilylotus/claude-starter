package cn.nihility.rbac.sync.openapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 分页拉取数据域当前数据的请求参数（app-sync-drop-changelog change design.md
 * Decision 2/3/4/5）。多个过滤条件同时提供时按交集处理；{@code codes} 在
 * {@code dataType=POSITION} 时、{@code mobile} 在 {@code dataType != USER} 时均被静默
 * 忽略，不视为参数错误。{@code dataType} 必填由 Controller 层 {@code @RequestParam}
 * （缺失时抛 {@code MissingServletRequestParameterException}）与 Service 层
 * {@code assertValidDataType}（非法取值时抛 {@link cn.nihility.rbac.common.exception.BusinessException}）
 * 共同保证，不在本 DTO 上重复声明 Bean Validation 注解。
 */
@Getter
@Builder
@Schema(description = "分页拉取请求参数")
public class SyncPullRequest {

    /** 数据类型：ORG/USER/POSITION/APP/ROLE/DICT。 */
    @Schema(description = "数据类型：ORG/USER/POSITION/APP/ROLE/DICT")
    private final String dataType;

    /** 页码，未传时默认第 1 页。 */
    @Schema(description = "页码，未传时默认第 1 页")
    private final Integer page;

    /** 每页大小，未传或非正数时取该应用该数据域配置的拉取分页大小。 */
    @Schema(description = "每页大小，未传或非正数时取该应用该数据域配置的拉取分页大小")
    private final Integer pageSize;

    /** 更新时间范围起点（含），用于增量拉取，可为空。 */
    @Schema(description = "更新时间范围起点（含），用于增量拉取")
    private final LocalDateTime updateTimeFrom;

    /** 更新时间范围终点（含），可为空。 */
    @Schema(description = "更新时间范围终点（含）")
    private final LocalDateTime updateTimeTo;

    /** 主键 id 列表精确过滤，可为空。 */
    @Schema(description = "主键 id 列表精确过滤")
    private final List<Long> ids;

    /** 业务编码列表精确过滤，可为空；POSITION 数据类型不支持，传入时被忽略。 */
    @Schema(description = "业务编码列表精确过滤，任职数据类型不支持，传入时被忽略")
    private final List<String> codes;

    /** 用户手机号精确过滤，可为空；仅 dataType=USER 时生效，其余数据类型传入时被忽略。 */
    @Schema(description = "用户手机号精确过滤，仅 dataType=USER 时生效，其余数据类型传入时被忽略")
    private final String mobile;
}
