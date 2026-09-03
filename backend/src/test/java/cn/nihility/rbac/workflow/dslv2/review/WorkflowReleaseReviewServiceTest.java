package cn.nihility.rbac.workflow.dslv2.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelVO;
import cn.nihility.rbac.workflow.designer.service.WorkflowProcessModelService;
import cn.nihility.rbac.workflow.entity.ReleaseReviewEntity;
import cn.nihility.rbac.workflow.mapper.ReleaseReviewMapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程模型发布审核服务真实数据库集成测试（production-approval-lifecycle change 第 4 节，
 * tasks.md 4.3）：编辑者/审核者分离、草稿修改后审核请求失效两条核心约束。
 */
@SpringBootTest
@Transactional
class WorkflowReleaseReviewServiceTest {

    @Autowired
    private WorkflowReleaseReviewService reviewService;
    @Autowired
    private WorkflowProcessModelService workflowProcessModelService;
    @Autowired
    private ReleaseReviewMapper releaseReviewMapper;

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Test
    void decide_shouldReject_whenReviewerSameAsEditor() {
        ProcessModelVO model = workflowProcessModelService.createModel(
                "TEST_V2_REVIEW_" + SEQ.incrementAndGet(), "审核测试", 1L);
        workflowProcessModelService.saveDraft(model.getId(), "{\"schemaVersion\":2}", null);

        Long reviewId = reviewService.submitForReview(model.getId(), 801001L);

        assertThatThrownBy(() -> reviewService.decide(reviewId, 801001L, true, "自己审核自己"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能与编辑者是同一人");
    }

    @Test
    void decide_shouldSucceed_whenReviewerDiffersFromEditor() {
        ProcessModelVO model = workflowProcessModelService.createModel(
                "TEST_V2_REVIEW_" + SEQ.incrementAndGet(), "审核测试", 1L);
        workflowProcessModelService.saveDraft(model.getId(), "{\"schemaVersion\":2}", null);

        Long reviewId = reviewService.submitForReview(model.getId(), 802001L);
        reviewService.decide(reviewId, 802002L, true, "同意发布");

        ReleaseReviewEntity review = releaseReviewMapper.selectById(reviewId);
        assertThat(review.getReviewStatus()).isEqualTo("APPROVED");
        assertThat(review.getReviewerId()).isEqualTo(802002L);

        reviewService.requireApprovedForCurrentRevision(model.getId());
    }

    @Test
    void decide_shouldInvalidateReview_whenDraftModifiedAfterSubmit() {
        ProcessModelVO model = workflowProcessModelService.createModel(
                "TEST_V2_REVIEW_" + SEQ.incrementAndGet(), "审核测试", 1L);
        workflowProcessModelService.saveDraft(model.getId(), "{\"schemaVersion\":2,\"processName\":\"v1\"}", null);

        Long reviewId = reviewService.submitForReview(model.getId(), 803001L);

        workflowProcessModelService.saveDraft(model.getId(), "{\"schemaVersion\":2,\"processName\":\"v2-modified\"}", null);

        assertThatThrownBy(() -> reviewService.decide(reviewId, 803002L, true, "同意"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已失效");

        assertThatThrownBy(() -> reviewService.requireApprovedForCurrentRevision(model.getId()))
                .isInstanceOf(BusinessException.class);
    }
}
