package cn.nihility.rbac.operationlog.controller;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.operationlog.dto.OperationLogDetailVO;
import cn.nihility.rbac.operationlog.dto.OperationLogQueryRequest;
import cn.nihility.rbac.operationlog.dto.OperationLogVO;
import cn.nihility.rbac.operationlog.service.OperationLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志管理接口，提供分页查询（支持按模块/资源类型/操作类型/操作人/操作时间范围
 * 筛选）与详情查询能力，只读，不提供新增/编辑/删除接口——写入只通过
 * {@code OperationLogRecorder} 在各业务模块内部完成。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "操作日志管理", description = "操作日志分页查询、详情查询接口，只读")
public class OperationLogController {

    /** 操作日志查询业务逻辑接口。 */
    private final OperationLogQueryService operationLogQueryService;

    /**
     * 分页查询操作日志，全部筛选参数可选，按操作发起时间降序排列。
     *
     * @param module        业务模块中文名，精确匹配，可选
     * @param resourceType  资源类型编码，精确匹配，可选
     * @param targetId      被操作对象主键 id，精确匹配，可选，通常与 resourceType 组合使用
     * @param operationType 操作类型，可选
     * @param createBy      操作人，精确匹配，可选
     * @param startTime     操作发起时间范围起点（含），可选
     * @param endTime       操作发起时间范围终点（含），可选
     * @param page          页码，默认第 1 页
     * @param pageSize      每页条数，默认 10 条
     * @return 操作日志的分页结果
     */
    @Operation(summary = "分页查询操作日志",
            description = "支持按模块/资源类型/被操作对象id/操作类型/操作人/操作时间范围筛选，均可选，按操作发起时间降序排列；"
                    + "resourceType + targetId 组合可查询单个资源实例的操作历史")
    @GetMapping("/api/operation-logs")
    public PageResult<OperationLogVO> page(
            @Parameter(description = "业务模块中文名，精确匹配")
            @RequestParam(required = false) String module,
            @Parameter(description = "资源类型编码，精确匹配")
            @RequestParam(required = false) String resourceType,
            @Parameter(description = "被操作对象主键 id，精确匹配，通常与 resourceType 组合使用")
            @RequestParam(required = false) Long targetId,
            @Parameter(description = "操作类型：1=新增，2=编辑，3=启用，4=停用，5=删除")
            @RequestParam(required = false) Integer operationType,
            @Parameter(description = "操作人，精确匹配")
            @RequestParam(required = false) String createBy,
            @Parameter(description = "操作发起时间范围起点（含），如 2026-07-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "操作发起时间范围终点（含），如 2026-07-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        OperationLogQueryRequest request = new OperationLogQueryRequest();
        request.setModule(module);
        request.setResourceType(resourceType);
        request.setTargetId(targetId);
        request.setOperationType(operationType);
        request.setCreateBy(createBy);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setPage(page);
        request.setPageSize(pageSize);
        return operationLogQueryService.getPage(request);
    }

    /**
     * 查询操作日志详情，含结构化的字段变更列表。
     *
     * @param id 操作日志 id
     * @return 操作日志详情
     */
    @Operation(summary = "查询操作日志详情", description = "返回操作日志完整信息及结构化的字段变更列表")
    @GetMapping("/api/operation-logs/{id}")
    public OperationLogDetailVO detail(@PathVariable Long id) {
        return operationLogQueryService.getById(id);
    }
}
