package cn.nihility.rbac.chat.mapper;

import cn.nihility.rbac.chat.entity.ChatUserKeyEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天密钥目录 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 无需自定义 SQL。
 */
@Mapper
public interface ChatUserKeyMapper extends BaseMapper<ChatUserKeyEntity> {
}
