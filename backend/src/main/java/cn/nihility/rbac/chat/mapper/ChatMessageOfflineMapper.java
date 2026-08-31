package cn.nihility.rbac.chat.mapper;

import cn.nihility.rbac.chat.entity.ChatMessageOfflineEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 离线消息队列 MyBatis-Plus 数据访问接口，单表 CRUD 与分页查询直接复用 {@link BaseMapper}，
 * 无需自定义 SQL。
 */
@Mapper
public interface ChatMessageOfflineMapper extends BaseMapper<ChatMessageOfflineEntity> {
}
