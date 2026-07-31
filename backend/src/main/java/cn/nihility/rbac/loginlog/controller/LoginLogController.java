package cn.nihility.rbac.loginlog.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.loginlog.dto.LoginLogQueryRequest;
import cn.nihility.rbac.loginlog.dto.LoginLogVO;
import cn.nihility.rbac.loginlog.service.LoginLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志管理接口，提供分页查询（支持按登录账号/登录结果/登录时间范围筛选）能力，
 * 只读，不提供新增/编辑/删除接口——写入只通过 {@code LoginLogRecorder} 在
 * {@code AuthServiceImpl#login} 内部完成；也不提供详情接口，列表行本身已含完整字段。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "登录日志管理", description = "登录日志分页查询接口，只读")
public class LoginLogController {

    /** 登录日志查询业务逻辑接口。 */
    private final LoginLogQueryService loginLogQueryService;

    /**
     * 分页查询登录日志，全部筛选参数可选，按登录发起时间降序排列。
     *
     * @param loginAccount 登录账号，精确匹配，可选
     * @param loginResult  登录结果，可选
     * @param startTime    登录时间范围起点（含），可选
     * @param endTime      登录时间范围终点（含），可选
     * @param page         页码，默认第 1 页
     * @param pageSize     每页条数，默认 10 条
     * @return 登录日志的分页结果
     */
    @Operation(summary = "分页查询登录日志",
            description = "支持按登录账号/登录结果/登录时间范围筛选，均可选，按登录发起时间降序排列")
    @GetMapping("/api/login-logs")
    public PageResult<LoginLogVO> page(
            @Parameter(description = "登录账号，精确匹配")
            @RequestParam(required = false) String loginAccount,
            @Parameter(description = "登录结果：1=成功，2=失败")
            @RequestParam(required = false) Integer loginResult,
            @Parameter(description = "登录时间范围起点（含），如 2026-07-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "登录时间范围终点（含），如 2026-07-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        LoginLogQueryRequest request = new LoginLogQueryRequest();
        request.setLoginAccount(loginAccount);
        request.setLoginResult(loginResult);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setPage(page);
        request.setPageSize(pageSize);
        return loginLogQueryService.getPage(request);
    }
}
