package cn.nihility.rbac.approval.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.approval.dto.ApprovalSwitchVO;
import cn.nihility.rbac.approval.entity.ApprovalSwitchEntity;
import cn.nihility.rbac.approval.mapper.ApprovalSwitchMapper;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ApprovalSwitchServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalSwitchServiceImplTest {

    @Mock
    private ApprovalSwitchMapper mapper;

    @Mock
    private OperationLogRecorder operationLogRecorder;

    private ApprovalSwitchServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 列缓存。 */
    @BeforeAll
    static void primeLambdaColumnCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "approvalSwitchTest");
        assistant.setCurrentNamespace(ApprovalSwitchEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, ApprovalSwitchEntity.class);
    }

    /** 构造被测服务。 */
    @BeforeEach
    void setUp() {
        service = new ApprovalSwitchServiceImpl(mapper, operationLogRecorder);
        CurrentUserContext.setUserId(1L);
    }

    /** 清理线程上下文。 */
    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    /** 查询时应返回四类开关。 */
    @Test
    void listAll_shouldReturnSwitches() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                buildEntity(1L, "ORG", true),
                buildEntity(2L, "USER", true),
                buildEntity(3L, "POSITION", true),
                buildEntity(4L, "APP", true)));

        List<ApprovalSwitchVO> result = service.listAll();

        assertThat(result).hasSize(4).allMatch(ApprovalSwitchVO::getEnabled);
    }

    /** 修改开关应持久化并记录操作日志。 */
    @Test
    void update_shouldPersistAndRecordLog() {
        ApprovalSwitchEntity entity = buildEntity(1L, "ORG", true);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        ApprovalSwitchVO result = service.update("ORG", false);

        assertThat(result.getEnabled()).isFalse();
        verify(mapper).updateById(entity);
        verify(operationLogRecorder).recordUpdate(any(), any(), any(), any(), any());
    }

    /** 非法业务对象类型应被拒绝。 */
    @Test
    void update_shouldRejectUnsupportedBizType() {
        assertThatThrownBy(() -> service.update("ROLE", true))
                .isInstanceOf(BusinessException.class);
    }

    /** 构造审批开关实体。 */
    private ApprovalSwitchEntity buildEntity(Long id, String bizType, boolean enabled) {
        return ApprovalSwitchEntity.builder().id(id).bizType(bizType).enabled(enabled).build();
    }
}
