package cn.nihility.rbac.sync.openapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 增量游标拉取变更指针的请求参数（app-sync-changelog-pull change design.md Decision 4）。
 * {@code entityType} 必填由 Controller 层 {@code @RequestParam}（缺失时抛
 * {@code MissingServletRequestParameterException}）与 Service 层校验（非法取值时抛
 * {@link cn.nihility.rbac.common.exception.BusinessException}）共同保证。
 */
@Getter
@Builder
@Schema(description = "增量游标拉取变更指针请求参数")
public class SyncChangesRequest {

    /** 数据类型：ORG/USER/POSITION/APP/ROLE/DICT。 */
    @Schema(description = "数据类型：ORG/USER/POSITION/APP/ROLE/DICT")
    private final String entityType;

    /**
     * 起始游标（不含，只返回 {@code changeSeq > sinceSeq} 的记录），十进制字符串，未传时
     * 视为 {@code "0"}（从头开始）。
     */
    @Schema(description = "起始游标（不含），十进制字符串，未传时视为 \"0\"（从头开始）")
    private final String sinceSeq;

    /** 每页最多返回的可见记录数，未传或非正数时取默认值。 */
    @Schema(description = "每页最多返回的可见记录数，未传或非正数时取默认值")
    private final Integer pageSize;
}
