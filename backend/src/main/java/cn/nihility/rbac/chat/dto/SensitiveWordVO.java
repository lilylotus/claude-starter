package cn.nihility.rbac.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 敏感词详情视图对象。
 */
@Getter
@Setter
@Schema(description = "敏感词详情")
public class SensitiveWordVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 敏感词词条。 */
    @Schema(description = "敏感词词条")
    private String word;

    /** 状态：2000=启用，3000=停用。 */
    @Schema(description = "状态：2000=启用，3000=停用")
    private Integer status;

    /** 创建人展示名。 */
    @Schema(description = "创建人展示名")
    private String createBy;

    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新人展示名。 */
    @Schema(description = "更新人展示名")
    private String updateBy;

    /** 更新时间。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
