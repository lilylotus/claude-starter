package cn.nihility.rbac.video.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 视频批量逻辑删除请求，对不存在/已删除的 id 跳过而非整体失败，因此只校验
 * {@code ids} 非空。
 */
@Getter
@Setter
@Schema(description = "视频批量逻辑删除请求")
public class VideoBatchDeleteRequest {

    /** 待删除的视频记录 id 列表，不能为空。 */
    @NotEmpty(message = "待删除的视频 id 列表不能为空")
    @Schema(description = "待删除的视频记录 id 列表")
    private List<Long> ids;
}
