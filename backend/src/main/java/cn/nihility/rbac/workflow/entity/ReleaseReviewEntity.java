package cn.nihility.rbac.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 流程模型发布审核记录持久化实体，对应表 {@code tab_wf_release_review}。编辑者与审核者
 * 分离，修改草稿后此前审核记录随 {@code draftRevision}/{@code artifactDigest} 失效但不删除
 * （production-approval-lifecycle change design.md Decision 4）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_release_review")
public class ReleaseReviewEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属流程模型 id，关联 {@code tab_wf_process_model.id}。 */
    private Long processModelId;

    /** 发起审核时的草稿修订号快照。 */
    private Long draftRevision;

    /** 发起审核时的 DSL 产物摘要，草稿再次修改后与最新摘要不一致即视为审核失效。 */
    private String artifactDigest;

    /** 提交审核的编辑者用户 id。 */
    private Long editorId;

    /** 审核者用户 id，做出审核决策后回填，且不能与 editorId 相同。 */
    private Long reviewerId;

    /** 审核状态：PENDING/APPROVED/REJECTED。 */
    private String reviewStatus;

    /** 审核意见。 */
    private String reviewOpinion;

    /** 关联的测试环境试运行报告引用 id。 */
    private String testReportRef;

    /** 提交审核时间。 */
    private LocalDateTime submitTime;

    /** 审核决策时间。 */
    private LocalDateTime reviewTime;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
