package cn.nihility.rbac.identity.upstream.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.identity.upstream.constant.UpstreamSyncRecordDetailStatus;
import cn.nihility.rbac.identity.upstream.constant.UpstreamSyncStatus;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSyncRecordDetailVO;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSyncRecordVO;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordDetailEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordDetailMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UpstreamSyncRecordServiceImpl} 的单元测试，覆盖
 * upstream-sync-record-improvements change 新增的分页查询记录列表、按记录 id 分页查询
 * 行明细（含越权场景）能力（tasks.md 6.2）。
 */
@ExtendWith(MockitoExtension.class)
class UpstreamSyncRecordServiceImplTest {

    /** 被测组件的同步执行记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamSyncRecordMapper upstreamSyncRecordMapper;

    /** 被测组件的同步执行记录明细数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamSyncRecordDetailMapper upstreamSyncRecordDetailMapper;

    /** 被测组件实例。 */
    private UpstreamSyncRecordServiceImpl upstreamSyncRecordService;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        upstreamSyncRecordService = new UpstreamSyncRecordServiceImpl(upstreamSyncRecordMapper,
                upstreamSyncRecordDetailMapper);
    }

    /**
     * 分页查询某数据源的同步执行记录列表，应返回该页记录转换后的视图对象与总条数。
     */
    @Test
    void listBySource_shouldReturnPagedRecords() {
        UpstreamSyncRecordEntity entity = UpstreamSyncRecordEntity.builder()
                .id(1L).sourceId(10L).dataType("ORG").triggerType("MANUAL").status(UpstreamSyncStatus.SUCCESS)
                .totalCount(5).successCount(5).failCount(0).build();
        Page<UpstreamSyncRecordEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(entity));
        when(upstreamSyncRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(resultPage);

        PageResult<UpstreamSyncRecordVO> result = upstreamSyncRecordService.listBySource(10L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getId()).isEqualTo(1L);
        assertThat(result.getRecords().get(0).getTotalCount()).isEqualTo(5);
    }

    /**
     * 按执行记录 id 分页查询行明细：记录确实属于指定数据源时，应返回该页明细转换后的
     * 视图对象与总条数。
     */
    @Test
    void listDetailsByRecord_shouldReturnPagedDetails_whenRecordBelongsToSource() {
        UpstreamSyncRecordEntity record = UpstreamSyncRecordEntity.builder().id(2L).sourceId(10L).build();
        when(upstreamSyncRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(record);

        UpstreamSyncRecordDetailEntity detail = UpstreamSyncRecordDetailEntity.builder()
                .id(100L).syncRecordId(2L).sourceId(10L).rowNo(1).rowData("{\"code\":\"ORG001\"}")
                .status(UpstreamSyncRecordDetailStatus.SUCCESS).build();
        Page<UpstreamSyncRecordDetailEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(detail));
        when(upstreamSyncRecordDetailMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(resultPage);

        PageResult<UpstreamSyncRecordDetailVO> result = upstreamSyncRecordService.listDetailsByRecord(10L, 2L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getRowNo()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getRowData()).contains("ORG001");
    }

    /**
     * 记录 id 与数据源 id 组合不匹配（如猜测其他数据源的记录 id）时，应拒绝查询，避免
     * 越权查看其他数据源的明细（design.md Decision 4）。
     */
    @Test
    void listDetailsByRecord_shouldReject_whenRecordDoesNotBelongToSource() {
        when(upstreamSyncRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> upstreamSyncRecordService.listDetailsByRecord(10L, 999L, 1, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }
}
