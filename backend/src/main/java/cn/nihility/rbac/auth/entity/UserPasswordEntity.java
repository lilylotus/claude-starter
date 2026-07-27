package cn.nihility.rbac.auth.entity;

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
 * 用户密码持久化实体，对应表 {@code tab_user_password}。一个用户仅保留一条当前有效的
 * 密码记录（{@code userId} 唯一），改密即整行 {@code UPDATE}，不保留历史密码
 * （password-login-auth change design.md Decision 1）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_user_password")
public class UserPasswordEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 id，关联 {@code tab_user.id}，唯一。 */
    private Long userId;

    /** 密码摘要，{@code SHA-256(明文密码 + 盐值)} 的十六进制小写字符串。 */
    private String passwordDigest;

    /** 摘要盐值，{@code SecureRandom} 随机生成，十六进制编码。 */
    private String salt;

    /** 是否处于待首次登录强制改密状态：{@code true}=是，{@code false}=否。 */
    private Boolean firstLogin;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
