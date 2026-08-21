package cn.nihility.rbac.ssoprotocollog.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogQueryRequest;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogVO;
import cn.nihility.rbac.ssoprotocollog.service.SsoProtocolLogQueryService;
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
 * SSO 协议调用记录查询接口，提供分页查询（支持按应用/协议类型/事件类型/调用结果/调用时间
 * 范围筛选）能力，只读，不提供新增/编辑/删除接口——写入只通过 {@code SsoProtocolLogRecorder}
 * 在 CAS/OAuth2 协议运行时端点内部完成；也不提供详情接口，列表行本身已含完整字段
 * （add-sso-protocol-access-log change design.md Decision 6）。供问题排查使用，不新增菜单/
 * 按钮，不接入权限校验注解（与 {@code LoginLogController} 既有先例保持一致，本代码库对
 * 日志类查询接口不做后端权限点校验）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "SSO 协议调用记录", description = "CAS/OAuth2.0 协议运行时端点调用记录分页查询接口，只读")
public class SsoProtocolLogController {

    /** SSO 协议调用记录查询业务逻辑接口。 */
    private final SsoProtocolLogQueryService ssoProtocolLogQueryService;

    /**
     * 分页查询 SSO 协议调用记录，全部筛选参数可选，按调用发生时间降序排列。
     *
     * @param appRefId  应用 id，精确匹配，可选
     * @param protocol  协议类型，精确匹配，可选
     * @param eventType 事件类型，精确匹配，可选
     * @param result    调用结果，精确匹配，可选
     * @param sessionId SSO 会话标识（会话令牌摘要），精确匹配，可选，主要供"登录日志"页面
     *                  查询某次登录之后关联的协议调用记录使用
     * @param startTime 调用时间范围起点（含），可选
     * @param endTime   调用时间范围终点（含），可选
     * @param page      页码，默认第 1 页
     * @param pageSize  每页条数，默认 10 条
     * @return SSO 协议调用记录的分页结果
     */
    @Operation(summary = "分页查询 SSO 协议调用记录",
            description = "支持按应用/协议类型/事件类型/调用结果/SSO 会话标识/调用时间范围筛选，均可选，按调用发生时间降序排列")
    @GetMapping("/api/sso-protocol-logs")
    public PageResult<SsoProtocolLogVO> page(
            @Parameter(description = "应用 id，精确匹配")
            @RequestParam(required = false) Long appRefId,
            @Parameter(description = "协议类型：CAS/OAUTH2/NONE")
            @RequestParam(required = false) String protocol,
            @Parameter(description = "事件类型：LOGIN/SERVICE_VALIDATE/LOGOUT/AUTHORIZE/TOKEN/USERINFO")
            @RequestParam(required = false) String eventType,
            @Parameter(description = "调用结果：1=成功，2=失败")
            @RequestParam(required = false) Integer result,
            @Parameter(description = "SSO 会话标识（会话令牌摘要），精确匹配")
            @RequestParam(required = false) String sessionId,
            @Parameter(description = "调用时间范围起点（含），如 2026-07-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "调用时间范围终点（含），如 2026-07-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        SsoProtocolLogQueryRequest request = new SsoProtocolLogQueryRequest();
        request.setAppRefId(appRefId);
        request.setProtocol(protocol);
        request.setEventType(eventType);
        request.setResult(result);
        request.setSessionId(sessionId);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setPage(page);
        request.setPageSize(pageSize);
        return ssoProtocolLogQueryService.getPage(request);
    }
}
