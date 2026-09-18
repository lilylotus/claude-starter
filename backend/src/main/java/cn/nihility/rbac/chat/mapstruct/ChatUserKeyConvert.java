package cn.nihility.rbac.chat.mapstruct;

import cn.nihility.rbac.chat.dto.ChatUserKeyVO;
import cn.nihility.rbac.chat.entity.ChatUserKeyEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 聊天密钥目录实体与视图对象之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态单例调用。
 */
@Mapper
public interface ChatUserKeyConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    ChatUserKeyConvert INSTANCE = Mappers.getMapper(ChatUserKeyConvert.class);

    /**
     * 密钥目录实体转视图对象。
     *
     * @param entity 密钥目录实体
     * @return 视图对象
     */
    ChatUserKeyVO toVO(ChatUserKeyEntity entity);
}
