package cn.nihility.rbac.workflow.outbox.mapper;

import cn.nihility.rbac.workflow.outbox.entity.EventConsumeEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Outbox 事件消费去重记录数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}
 * （production-approval-lifecycle change tasks.md 7.2）。消费唯一键判重通过 {@code insert}
 * 命中 {@code (event_id, consumer_code)} 唯一约束冲突实现，不需要自定义查询 SQL。
 */
@Mapper
public interface EventConsumeMapper extends BaseMapper<EventConsumeEntity> {
}
