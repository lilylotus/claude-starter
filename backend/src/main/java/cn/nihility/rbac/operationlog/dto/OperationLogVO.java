package cn.nihility.rbac.operationlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 操作日志列表行视图对象，含字段级变更详情（用于详情页历史面板默认展开展示），
 * 不含原始 User-Agent，避免列表响应体过大。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "操作日志列表行")
public class OperationLogVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 业务模块中文名，如"组织管理"。 */
    @Schema(description = "业务模块中文名")
    private String module;

    /** 资源类型编码，如 org。 */
    @Schema(description = "资源类型编码")
    private String resourceType;

    /** 资源类型中文名，如"组织"。 */
    @Schema(description = "资源类型中文名")
    private String resourceName;

    /** 操作类型：1=新增，2=编辑，3=启用，4=停用，5=删除。 */
    @Schema(description = "操作类型：1=新增，2=编辑，3=启用，4=停用，5=删除")
    private Integer operationType;

    /** 操作类型中文文案。 */
    @Schema(description = "操作类型中文文案")
    private String operationTypeLabel;

    /** 操作来源：0=界面操作，1=Excel导入。 */
    @Schema(description = "操作来源：0=界面操作，1=Excel导入")
    private Integer operateSource;

    /** 操作来源中文文案。 */
    @Schema(description = "操作来源中文文案")
    private String operateSourceLabel;

    /** 被操作对象主键 id。 */
    @Schema(description = "被操作对象主键 id")
    private Long targetId;

    /** 被操作对象名称快照。 */
    @Schema(description = "被操作对象名称快照")
    private String targetName;

    /** 操作发起 IP，可为空。 */
    @Schema(description = "操作发起 IP")
    private String operateIp;

    /** 操作终端类型，可为空。 */
    @Schema(description = "操作终端类型")
    private String operateTerminal;

    /** 操作系统，可为空。 */
    @Schema(description = "操作系统")
    private String operateOs;

    /** 操作浏览器，可为空。 */
    @Schema(description = "操作浏览器")
    private String operateBrowser;

    /** 操作人。 */
    @Schema(description = "操作人")
    private String createBy;

    /** 操作发起时间。 */
    @Schema(description = "操作发起时间")
    private LocalDateTime createTime;

    /** 字段级变更详情，新增/删除操作时对应记录的 {@code oldValue}/{@code newValue} 可为空。 */
    @Schema(description = "字段级变更详情")
    private List<OperationLogFieldChangeVO> changeDetail;
}
