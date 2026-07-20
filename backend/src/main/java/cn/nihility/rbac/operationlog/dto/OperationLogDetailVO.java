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
 * 操作日志详情视图对象，在列表行字段基础上，额外携带原始 User-Agent 与结构化的
 * 字段变更列表。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "操作日志详情")
public class OperationLogDetailVO {

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

    /** 原始 User-Agent 请求头，可为空。 */
    @Schema(description = "原始 User-Agent 请求头")
    private String operateUserAgent;

    /** 操作人。 */
    @Schema(description = "操作人")
    private String createBy;

    /** 操作发起时间。 */
    @Schema(description = "操作发起时间")
    private LocalDateTime createTime;

    /** 结构化的字段变更列表。 */
    @Schema(description = "字段变更列表")
    private List<OperationLogFieldChangeVO> changeDetail;
}
