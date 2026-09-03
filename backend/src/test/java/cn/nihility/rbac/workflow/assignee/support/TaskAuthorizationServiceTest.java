package cn.nihility.rbac.workflow.assignee.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.workflow.constant.CandidateType;
import cn.nihility.rbac.workflow.entity.ApprovalTaskCandidateEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskCandidateMapper;
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
 * {@link TaskAuthorizationService} 单元测试（spec.md "任务处理越权校验" Requirement）。
 */
@ExtendWith(MockitoExtension.class)
class TaskAuthorizationServiceTest {

    @Mock
    private ApprovalTaskCandidateMapper approvalTaskCandidateMapper;

    @Mock
    private AdminRoleLookupService adminRoleLookupService;

    private TaskAuthorizationService service;

    /** 初始化 MyBatis-Plus Lambda 列缓存。 */
    @BeforeAll
    static void primeLambdaColumnCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "taskAuthorizationTest");
        assistant.setCurrentNamespace(ApprovalTaskCandidateEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, ApprovalTaskCandidateEntity.class);
    }

    void setUp() {
        service = new TaskAuthorizationService(approvalTaskCandidateMapper, adminRoleLookupService);
    }

    /** 指定处理人本人应有权处理。 */
    @Test
    void isAuthorized_shouldAllowAssignee() {
        setUp();
        ApprovalTaskEntity task = ApprovalTaskEntity.builder().id(1L).assigneeId(100L).build();

        assertThat(service.isAuthorized(task, 100L)).isTrue();
    }

    /** 候选人明细命中用户维度时应有权处理。 */
    @Test
    void isAuthorized_shouldAllowUserCandidate() {
        setUp();
        ApprovalTaskEntity task = ApprovalTaskEntity.builder().id(1L).assigneeId(null).build();
        when(approvalTaskCandidateMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

        assertThat(service.isAuthorized(task, 200L)).isTrue();
    }

    /** 持有候选角色维度指定角色时应有权处理。 */
    @Test
    void isAuthorized_shouldAllowRoleCandidate() {
        setUp();
        ApprovalTaskEntity task = ApprovalTaskEntity.builder().id(1L).assigneeId(null).build();
        when(approvalTaskCandidateMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
        when(approvalTaskCandidateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                ApprovalTaskCandidateEntity.builder().taskId(1L).candidateType(CandidateType.ROLE)
                        .candidateValue("SECURITY_ADMIN").build()));
        when(adminRoleLookupService.userHasRoleCode(300L, "SECURITY_ADMIN")).thenReturn(true);

        assertThat(service.isAuthorized(task, 300L)).isTrue();
    }

    /** 三维度均未命中时应拒绝。 */
    @Test
    void isAuthorized_shouldRejectWhenNoneMatches() {
        setUp();
        ApprovalTaskEntity task = ApprovalTaskEntity.builder().id(1L).assigneeId(100L).build();
        when(approvalTaskCandidateMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
        when(approvalTaskCandidateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThat(service.isAuthorized(task, 999L)).isFalse();
    }
}
