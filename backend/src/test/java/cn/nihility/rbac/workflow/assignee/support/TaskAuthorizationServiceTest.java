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

    /**
     * 初始化 MyBatis-Plus Lambda 列缓存。必须显式开启 {@code mapUnderscoreToCamelCase}（与
     * 本项目 {@code mybatis/mybatis.conf} 里真实 MyBatis 配置一致），否则默认关闭该项的
     * {@link Configuration} 会让 {@code TableInfoHelper} 这个 JVM 静态缓存把列名错误地永久
     * 缓存成驼峰字段名本身，污染同一实体后续所有真实 {@code @SpringBootTest} 集成测试生成的
     * SQL（fix-approval-zero-task-process-completion change 实施时发现并修复）。
     */
    @BeforeAll
    static void primeLambdaColumnCache() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "taskAuthorizationTest");
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

    /** 未分配任务、三维度均未命中时应拒绝。 */
    @Test
    void isAuthorized_shouldRejectWhenNoneMatches() {
        setUp();
        ApprovalTaskEntity task = ApprovalTaskEntity.builder().id(1L).assigneeId(null).build();
        when(approvalTaskCandidateMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
        when(approvalTaskCandidateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThat(service.isAuthorized(task, 999L)).isFalse();
    }

    /**
     * 任务已分配给他人时，即便操作人仍留在候选人明细表里（认领/分配后从不清理该表），也应直接
     * 拒绝，不再查询候选人维度——候选人身份只对"未分配"任务有权（design.md 第8节，tasks.md
     * 6.5，此前实现遗漏：只要 operatorId 命中候选人表就放行，未校验任务是否已经分配给了别人）。
     * 不 stub 候选人/角色查询方法，用来证明短路发生在查询候选人表之前。
     */
    @Test
    void isAuthorized_shouldRejectOtherCandidate_whenAlreadyAssignedToSomeoneElse() {
        setUp();
        ApprovalTaskEntity task = ApprovalTaskEntity.builder().id(1L).assigneeId(100L).build();

        assertThat(service.isAuthorized(task, 999L)).isFalse();
    }
}
