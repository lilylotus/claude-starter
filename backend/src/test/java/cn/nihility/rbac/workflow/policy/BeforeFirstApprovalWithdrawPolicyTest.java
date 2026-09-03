package cn.nihility.rbac.workflow.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link BeforeFirstApprovalWithdrawPolicy} 单元测试（spec.md "撤回策略" Requirement）。
 */
@ExtendWith(MockitoExtension.class)
class BeforeFirstApprovalWithdrawPolicyTest {

    @Mock
    private ApprovalRecordMapper approvalRecordMapper;

    private BeforeFirstApprovalWithdrawPolicy policy;

    /** 初始化 MyBatis-Plus Lambda 列缓存。 */
    @BeforeAll
    static void primeLambdaColumnCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "withdrawPolicyTest");
        assistant.setCurrentNamespace(ApprovalRecordEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, ApprovalRecordEntity.class);
    }

    /** 尚无任何通过/驳回记录时允许撤回。 */
    @Test
    void canWithdraw_shouldAllowWhenNoApprovalRecordExists() {
        policy = new BeforeFirstApprovalWithdrawPolicy(approvalRecordMapper);
        when(approvalRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThat(policy.canWithdraw(1L, 100L)).isTrue();
    }

    /** 已存在通过/驳回记录时拒绝撤回。 */
    @Test
    void canWithdraw_shouldRejectWhenApprovalRecordExists() {
        policy = new BeforeFirstApprovalWithdrawPolicy(approvalRecordMapper);
        when(approvalRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(ApprovalRecordEntity.builder().action(ApprovalAction.APPROVE).build()));

        assertThat(policy.canWithdraw(1L, 100L)).isFalse();
    }

    /** 流程实例 id 为空时拒绝撤回。 */
    @Test
    void canWithdraw_shouldRejectWhenProcessInstanceIdIsNull() {
        policy = new BeforeFirstApprovalWithdrawPolicy(approvalRecordMapper);

        assertThat(policy.canWithdraw(null, 100L)).isFalse();
    }
}
