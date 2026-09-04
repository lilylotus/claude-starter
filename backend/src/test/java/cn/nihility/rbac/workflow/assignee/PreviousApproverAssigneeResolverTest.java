package cn.nihility.rbac.workflow.assignee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
 * {@link PreviousApproverAssigneeResolver} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PreviousApproverAssigneeResolverTest {

    @Mock
    private ApprovalRecordMapper approvalRecordMapper;

    private PreviousApproverAssigneeResolver resolver;

    /** 初始化 MyBatis-Plus Lambda 列缓存。 */
    @BeforeAll
    static void primeLambdaColumnCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "previousApproverTest");
        assistant.setCurrentNamespace(ApprovalRecordEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, ApprovalRecordEntity.class);
    }

    /** 应返回最近一条通过记录的操作人。 */
    @Test
    void resolve_shouldReturnLastApprover() {
        resolver = new PreviousApproverAssigneeResolver(approvalRecordMapper);
        when(approvalRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(
                List.of(ApprovalRecordEntity.builder().operatorId(500L).build()));

        var result = resolver.resolve(new AssigneeResolveContext(1L, "node2", null, 100L, 10L, null, null));

        assertThat(result).containsExactly(500L);
    }

    /** 无通过记录时返回空集合。 */
    @Test
    void resolve_shouldReturnEmptyWhenNoApprovalRecord() {
        resolver = new PreviousApproverAssigneeResolver(approvalRecordMapper);
        when(approvalRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var result = resolver.resolve(new AssigneeResolveContext(1L, "node2", null, 100L, 10L, null, null));

        assertThat(result).isEmpty();
    }

    /** 流程实例上下文缺失时返回空集合。 */
    @Test
    void resolve_shouldReturnEmptyWhenProcessInstanceMissing() {
        resolver = new PreviousApproverAssigneeResolver(approvalRecordMapper);

        var result = resolver.resolve(new AssigneeResolveContext(null, "node2", null, 100L, 10L, null, null));

        assertThat(result).isEmpty();
    }
}
