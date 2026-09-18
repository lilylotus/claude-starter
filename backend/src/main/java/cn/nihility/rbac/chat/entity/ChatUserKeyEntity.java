package cn.nihility.rbac.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 聊天密钥目录持久化实体，对应表 {@code tab_chat_user_key}。每个用户至多一条记录
 * （{@code userId} 唯一索引），存放本人 X25519 身份公钥（Base64）与后端留痕用的指纹
 * （design.md Decision 4/6）；私钥全程只保存在客户端浏览器本地，不经过服务端。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_chat_user_key")
public class ChatUserKeyEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id，关联 {@code tab_user.id}，唯一索引。 */
    private Long userId;

    /** X25519 身份公钥（Base64 编码，定长）。 */
    private String identityPublicKey;

    /** 身份公钥指纹，服务端留痕/审计用，非安全校验唯一依据（安全码比对在客户端完成）。 */
    private String keyFingerprint;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
