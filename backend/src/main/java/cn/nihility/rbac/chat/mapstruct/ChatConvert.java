package cn.nihility.rbac.chat.mapstruct;

import cn.nihility.rbac.chat.dto.ChatMessageVO;
import cn.nihility.rbac.chat.dto.ConversationMemberVO;
import cn.nihility.rbac.chat.dto.ConversationVO;
import cn.nihility.rbac.chat.dto.SensitiveWordCreateRequest;
import cn.nihility.rbac.chat.dto.SensitiveWordVO;
import cn.nihility.rbac.chat.entity.ChatMessageEntity;
import cn.nihility.rbac.chat.entity.ConversationEntity;
import cn.nihility.rbac.chat.entity.ConversationMemberEntity;
import cn.nihility.rbac.chat.entity.SensitiveWordEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 聊天模块实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态单例调用。
 */
@Mapper
public interface ChatConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    ChatConvert INSTANCE = Mappers.getMapper(ChatConvert.class);

    /**
     * 会话实体转会话列表视图对象；{@code name}（单聊场景需覆盖为对方展示名）、
     * {@code memberCount}、{@code lastMessage*} 均需由调用方另行解析并回填。
     *
     * @param entity 会话实体
     * @return 会话列表视图对象
     */
    @Mapping(target = "memberCount", ignore = true)
    @Mapping(target = "lastMessageContent", ignore = true)
    @Mapping(target = "lastMessageSenderId", ignore = true)
    @Mapping(target = "lastMessageSendTime", ignore = true)
    ConversationVO toConversationVO(ConversationEntity entity);

    /**
     * 会话成员实体转视图对象；{@code userName} 需由调用方另行解析并回填。
     *
     * @param entity 会话成员实体
     * @return 会话成员视图对象
     */
    @Mapping(target = "userName", ignore = true)
    ConversationMemberVO toMemberVO(ConversationMemberEntity entity);

    /**
     * 会话成员实体列表批量转视图对象列表。
     *
     * @param entities 会话成员实体列表
     * @return 会话成员视图对象列表
     */
    List<ConversationMemberVO> toMemberVOList(List<ConversationMemberEntity> entities);

    /**
     * 消息实体转历史消息视图对象；{@code senderName} 需由调用方另行解析并回填。
     *
     * @param entity 消息实体
     * @return 历史消息视图对象
     */
    @Mapping(target = "senderName", ignore = true)
    ChatMessageVO toMessageVO(ChatMessageEntity entity);

    /**
     * 消息实体列表批量转历史消息视图对象列表。
     *
     * @param entities 消息实体列表
     * @return 历史消息视图对象列表
     */
    List<ChatMessageVO> toMessageVOList(List<ChatMessageEntity> entities);

    /**
     * 敏感词实体转详情视图对象；{@code createBy}/{@code updateBy} entity 侧落库为用户 id
     * 文本，VO 侧需要展示为人可读展示名，两者语义不同，禁止 MapStruct 按同名字段直接复制，
     * 由调用方另行回填。
     *
     * @param entity 敏感词实体
     * @return 详情视图对象
     */
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    SensitiveWordVO toSensitiveWordVO(SensitiveWordEntity entity);

    /**
     * 敏感词实体列表批量转详情视图对象列表。
     *
     * @param entities 敏感词实体列表
     * @return 详情视图对象列表
     */
    List<SensitiveWordVO> toSensitiveWordVOList(List<SensitiveWordEntity> entities);

    /**
     * 创建请求转敏感词实体，id/状态/审计字段由服务层另行赋值。
     *
     * @param request 创建请求
     * @return 敏感词实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    SensitiveWordEntity toSensitiveWordEntity(SensitiveWordCreateRequest request);
}
