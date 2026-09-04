package cn.nihility.rbac.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 业务活动申请锁持久化实体，对应表 {@code tab_wf_business_lock}。复合主键
 * {@code (bizType, targetKey)}，没有单列自增主键，因此不使用 {@link com.baomidou.mybatisplus
 * .annotation.TableId} 注解，读写一律通过 {@code (bizType, targetKey)} 条件显式操作，不依赖
 * {@code BaseMapper} 里假设单列主键的 {@code selectById}/{@code updateById}/{@code deleteById}。
 * 锁行创建后长期保留复用（不随流程终态删除），只在 {@code activeRequestId} 上做"占用/释放"
 * 语义：{@code null} 表示空闲，非空表示被该 {@code tab_approval_request.id} 占用
 * （production-approval-lifecycle change design.md 第8节，tasks.md 6.2）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"bizType", "targetKey"})
@TableName("tab_wf_business_lock")
public class BusinessLockEntity {

    /** 业务对象类型：ORG/USER/POSITION/APP，复合主键分量之一。 */
    private String bizType;

    /** 业务目标标识（如目标记录 id 文本，CREATE 场景使用申请自身临时键），复合主键分量之一。 */
    private String targetKey;

    /** 当前占用该锁的活动申请 id（关联 {@code tab_approval_request.id}），为空表示锁行空闲。 */
    private Long activeRequestId;

    /** 审计修订号，每次占用或释放递增。 */
    private Long revision;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
