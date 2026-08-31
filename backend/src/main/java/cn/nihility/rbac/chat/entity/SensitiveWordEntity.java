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
 * 敏感词持久化实体，对应表 {@code tab_chat_sensitive_word}。服务启动时加载全部启用词条
 * 构建内存 AC 自动机，管理员增删改后触发自动机重建（见
 * {@link cn.nihility.rbac.chat.service.SensitiveWordFilterService}）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_chat_sensitive_word")
public class SensitiveWordEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 敏感词词条。 */
    private String word;

    /** 状态，见 {@link cn.nihility.rbac.chat.constant.SensitiveWordStatus}。 */
    private Integer status;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
