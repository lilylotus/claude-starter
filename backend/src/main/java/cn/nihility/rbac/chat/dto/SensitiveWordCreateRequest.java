package cn.nihility.rbac.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 新增敏感词的请求参数。
 */
@Getter
@Setter
@Schema(description = "新增敏感词请求参数")
public class SensitiveWordCreateRequest {

    /** 敏感词词条，需全局唯一。 */
    @NotBlank(message = "敏感词词条不能为空")
    @Size(max = 64, message = "敏感词词条长度不能超过 64 个字符")
    @Schema(description = "敏感词词条")
    private String word;
}
