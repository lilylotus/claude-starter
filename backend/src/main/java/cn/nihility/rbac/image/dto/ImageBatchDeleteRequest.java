package cn.nihility.rbac.image.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 图片批量逻辑删除请求（add-image-gallery change tasks.md 4.3）。对不存在/已删除的 id
 * 跳过而非整体失败（design.md Decision 9），因此本类只负责校验 {@code ids} 非空，
 * 不对元素做存在性校验。
 */
@Getter
@Setter
@Schema(description = "图片批量逻辑删除请求")
public class ImageBatchDeleteRequest {

    /** 待删除的图片记录 id 列表，不能为空。 */
    @NotEmpty(message = "待删除的图片 id 列表不能为空")
    @Schema(description = "待删除的图片记录 id 列表")
    private List<Long> ids;
}
