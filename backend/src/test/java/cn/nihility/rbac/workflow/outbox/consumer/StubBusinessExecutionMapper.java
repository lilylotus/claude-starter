package cn.nihility.rbac.workflow.outbox.consumer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试专用数据访问接口，配合 {@link StubBusinessExecutionEntity} 使用
 * （production-approval-lifecycle change tasks.md 7.2）。单表 CRUD 直接复用
 * {@link BaseMapper}，无需自定义查询 SQL。只存在于测试源码集，不会被打进生产制品。
 */
@Mapper
public interface StubBusinessExecutionMapper extends BaseMapper<StubBusinessExecutionEntity> {
}
