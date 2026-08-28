package cn.nihility.rbac.approval.controller;

import cn.nihility.rbac.approval.dto.ApprovalSwitchUpdateRequest;
import cn.nihility.rbac.approval.dto.ApprovalSwitchVO;
import cn.nihility.rbac.approval.service.ApprovalSwitchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批开关管理接口。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "审批设置", description = "组织、用户、任职、应用审批开关管理接口")
public class ApprovalSwitchController {

    /** 审批开关业务接口。 */
    private final ApprovalSwitchService approvalSwitchService;

    /**
     * 查询四类主数据审批开关。
     *
     * @return 审批开关列表
     */
    @Operation(summary = "查询审批开关")
    @GetMapping("/api/approval-switches")
    public List<ApprovalSwitchVO> list() {
        return approvalSwitchService.listAll();
    }

    /**
     * 修改指定业务对象的审批开关。
     *
     * @param bizType 业务对象类型
     * @param request 修改请求
     * @return 修改后的审批开关
     */
    @Operation(summary = "修改审批开关")
    @PutMapping("/api/approval-switches/{bizType}")
    public ApprovalSwitchVO update(
            @Parameter(description = "业务对象类型：ORG/USER/POSITION/APP", required = true)
            @PathVariable String bizType,
            @Valid @RequestBody ApprovalSwitchUpdateRequest request) {
        return approvalSwitchService.update(bizType, request.getEnabled());
    }
}
