package cn.nihility.rbac.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改应用接口签名算法的请求参数。
 */
@Getter
@Setter
@Schema(description = "修改签名算法请求参数")
public class SignAlgorithmUpdateRequest {

    /** 接口签名算法，必填，只允许 {@code SHA256}/{@code SM3}。 */
    @NotBlank(message = "签名算法不能为空")
    @Pattern(regexp = "^(SHA256|SM3)$", message = "签名算法只能是 SHA256 或 SM3")
    @Schema(description = "接口签名算法，只能是 SHA256 或 SM3")
    private String signAlgorithm;
}
