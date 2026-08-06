package cn.nihility.rbac.operationlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作日志分页查询的筛选参数，全部字段均可选。
 */
@Getter
@Setter
@Schema(description = "操作日志分页查询参数")
public class OperationLogQueryRequest {

    /** 业务模块中文名，精确匹配，可选。 */
    @Schema(description = "业务模块中文名，精确匹配")
    private String module;

    /** 资源类型编码，精确匹配，可选。 */
    @Schema(description = "资源类型编码，精确匹配")
    private String resourceType;

    /** 被操作对象主键 id，精确匹配，可选；与 resourceType 组合使用可查询单个资源实例的操作历史。 */
    @Schema(description = "被操作对象主键 id，精确匹配，通常与 resourceType 组合使用")
    private Long targetId;

    /** 操作类型，精确匹配，可选。 */
    @Schema(description = "操作类型：1=新增，2=编辑，3=启用，4=停用，5=删除")
    private Integer operationType;

    /** 操作人，精确匹配（前端输入的账号编码或姓名文本），可选。 */
    @Schema(description = "操作人，精确匹配")
    private String createBy;

    /**
     * 按 {@link #createBy}（前端输入的账号编码/姓名文本）解析出的候选用户 id 列表，
     * 由 service 层查询回填，不接受外部输入，不对外暴露。
     */
    private List<String> createByUserIds;

    /** 操作发起时间范围起点（含），可选。 */
    @Schema(description = "操作发起时间范围起点（含）")
    private LocalDateTime startTime;

    /** 操作发起时间范围终点（含），可选。 */
    @Schema(description = "操作发起时间范围终点（含）")
    private LocalDateTime endTime;

    /** 页码，默认第 1 页。 */
    @Schema(description = "页码，默认第 1 页", defaultValue = "1")
    private Integer page = 1;

    /** 每页条数，默认 10 条。 */
    @Schema(description = "每页条数，默认 10 条", defaultValue = "10")
    private Integer pageSize = 10;
}
