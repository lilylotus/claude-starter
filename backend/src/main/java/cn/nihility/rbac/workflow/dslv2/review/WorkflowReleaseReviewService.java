package cn.nihility.rbac.workflow.dslv2.review;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.dslv2.util.DigestUtils;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.entity.ReleaseReviewEntity;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import cn.nihility.rbac.workflow.mapper.ReleaseReviewMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 流程模型发布审核服务：编辑者提交审核、审核者做出决策，编辑者与审核者不能是同一人
 * （production-approval-lifecycle change design.md Decision 4"生产发布要求编辑者与发布
 * 审核者不是同一人"）。草稿在审核期间被再次修改（{@code draft_revision} 变化）会使已提交的
 * 审核请求失效，需要重新提交；审核历史不覆盖、不删除。
 */
@Service
@RequiredArgsConstructor
public class WorkflowReleaseReviewService {

    /** 发布审核记录数据访问接口。 */
    private final ReleaseReviewMapper releaseReviewMapper;

    /** 流程模型数据访问接口。 */
    private final ProcessModelMapper processModelMapper;

    /**
     * 编辑者提交当前草稿审核。
     *
     * @param modelId  流程模型 id
     * @param editorId 编辑者用户 id
     * @return 新建的审核记录 id
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public Long submitForReview(Long modelId, Long editorId) {
        ProcessModelEntity model = requireModel(modelId);
        if (!StringUtils.hasText(model.getModelJson())) {
            throw new BusinessException("流程模型草稿为空，无法提交审核");
        }
        if (editorId == null) {
            throw new BusinessException("提交审核必须携带编辑者身份");
        }
        LocalDateTime now = LocalDateTime.now();
        ReleaseReviewEntity review = ReleaseReviewEntity.builder()
                .processModelId(modelId)
                .draftRevision(model.getDraftRevision() == null ? 1L : model.getDraftRevision())
                .artifactDigest(DigestUtils.sha256(model.getModelJson()))
                .editorId(editorId)
                .reviewStatus("PENDING")
                .submitTime(now)
                .createBy(editorId.toString()).createTime(now).updateBy(editorId.toString()).updateTime(now)
                .build();
        releaseReviewMapper.insert(review);

        model.setDraftStatus("IN_REVIEW");
        model.setUpdateTime(now);
        processModelMapper.updateById(model);
        return review.getId();
    }

    /**
     * 审核者做出决策。审核者不能与编辑者相同；草稿在提交审核后又被修改（当前 draftRevision/
     * 摘要与提交时不一致）时审核请求已失效，拒绝决策，要求重新提交。
     *
     * @param reviewId   审核记录 id
     * @param reviewerId 审核者用户 id
     * @param approved   是否通过
     * @param opinion    审核意见
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void decide(Long reviewId, Long reviewerId, boolean approved, String opinion) {
        ReleaseReviewEntity review = releaseReviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("审核记录不存在");
        }
        if (!"PENDING".equals(review.getReviewStatus())) {
            throw new BusinessException("该审核请求已被处理，不能重复决策");
        }
        if (reviewerId == null || reviewerId.equals(review.getEditorId())) {
            throw new BusinessException("审核者不能与编辑者是同一人");
        }
        ProcessModelEntity model = requireModel(review.getProcessModelId());
        Long currentRevision = model.getDraftRevision() == null ? 1L : model.getDraftRevision();
        boolean stillValid = currentRevision.equals(review.getDraftRevision())
                && DigestUtils.sha256(model.getModelJson()).equals(review.getArtifactDigest());
        if (!stillValid) {
            review.setReviewStatus("REJECTED");
            review.setReviewOpinion("草稿在审核期间已被修改，审核请求自动失效，请重新提交");
            review.setReviewerId(reviewerId);
            review.setReviewTime(LocalDateTime.now());
            review.setUpdateTime(LocalDateTime.now());
            releaseReviewMapper.updateById(review);
            throw new BusinessException("草稿在审核期间已被修改（draft_revision/摘要不一致），审核请求已失效，请重新提交");
        }

        LocalDateTime now = LocalDateTime.now();
        review.setReviewStatus(approved ? "APPROVED" : "REJECTED");
        review.setReviewOpinion(opinion);
        review.setReviewerId(reviewerId);
        review.setReviewTime(now);
        review.setUpdateTime(now);
        releaseReviewMapper.updateById(review);

        model.setDraftStatus(approved ? "APPROVED_FOR_RELEASE" : "EDITING");
        model.setUpdateTime(now);
        processModelMapper.updateById(model);
    }

    /**
     * 校验流程模型当前是否具备一条针对当前草稿修订版本的有效 {@code APPROVED} 审核记录，
     * 供发布接口前置校验调用（design.md Decision 4"发布完成后仍需业务绑定才接收实际申请"
     * 之前的一步：发布本身要求审核通过）。
     *
     * @param modelId 流程模型 id
     * @throws BusinessException 不存在针对当前草稿版本的有效通过审核
     */
    public void requireApprovedForCurrentRevision(Long modelId) {
        ProcessModelEntity model = requireModel(modelId);
        Long currentRevision = model.getDraftRevision() == null ? 1L : model.getDraftRevision();
        List<ReleaseReviewEntity> approvedReviews = releaseReviewMapper.selectList(
                new LambdaQueryWrapper<ReleaseReviewEntity>()
                        .eq(ReleaseReviewEntity::getProcessModelId, modelId)
                        .eq(ReleaseReviewEntity::getDraftRevision, currentRevision)
                        .eq(ReleaseReviewEntity::getReviewStatus, "APPROVED"));
        if (approvedReviews.isEmpty()) {
            throw new BusinessException("当前草稿尚未通过发布审核（需要编辑者提交审核、另一位审核者批准后才能发布）");
        }
    }

    private ProcessModelEntity requireModel(Long modelId) {
        ProcessModelEntity model = processModelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException("流程模型不存在");
        }
        return model;
    }
}
