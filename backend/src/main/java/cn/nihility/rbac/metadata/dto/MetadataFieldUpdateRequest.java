package cn.nihility.rbac.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新元数据字段的请求参数。表名称/字段列名/字段类型/业务对象类型描述真实的数据库
 * 结构，创建（迁移写入）后不可修改，因此本请求只暴露 {@code fieldName} 一个可编辑
 * 属性；状态不通过本接口修改，需调用启用/停用专用接口。
 */
@Getter
@Setter
@Schema(description = "更新元数据字段请求参数")
public class MetadataFieldUpdateRequest {

    /** 字段名称，如"组织编码"。 */
    @NotBlank(message = "字段名称不能为空")
    @Size(max = 64, message = "字段名称长度不能超过 64 个字符")
    @Schema(description = "字段名称")
    private String fieldName;
}
