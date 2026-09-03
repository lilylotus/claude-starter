package cn.nihility.rbac.workflow.dslv2.review;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.workflow.dslv2.dto.ReviewDecisionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程模型发布审核接口（production-approval-lifecycle change design.md Decision 4/12）。
 * 权限门控通过 {@code IdentityAuthFilter} 依据请求头 {@code menu} 编码统一校验
 * （{@code WorkflowDesign:model:review}，见 权限资源.txt），本层不重复声明权限注解。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "流程发布审核", description = "提交审核/审核决策接口")
public class WorkflowReleaseReviewController {

    /** 发布审核服务。 */
    private final WorkflowReleaseReviewService workflowReleaseReviewService;

    /** 提交当前草稿审核。 */
    @Operation(summary = "提交流程模型发布审核")
    @PostMapping("/api/workflow/process-models/{modelId}/reviews")
    public Result<Long> submitForReview(@PathVariable Long modelId) {
        return Result.success(workflowReleaseReviewService.submitForReview(modelId, CurrentUserContext.getUserId()));
    }

    /** 审核决策。 */
    @Operation(summary = "流程模型发布审核决策")
    @PostMapping("/api/workflow/process-model-reviews/{reviewId}/decisions")
    public Result<Void> decide(@PathVariable Long reviewId, @Valid @RequestBody ReviewDecisionRequest request) {
        workflowReleaseReviewService.decide(reviewId, CurrentUserContext.getUserId(),
                Boolean.TRUE.equals(request.getApproved()), request.getOpinion());
        return Result.success();
    }
}
