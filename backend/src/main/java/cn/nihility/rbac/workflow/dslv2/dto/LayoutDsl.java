package cn.nihility.rbac.workflow.dslv2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 画布视口状态，仅前端往返使用，不参与编译（design.md Decision 3 DSL v2 示例）。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayoutDsl {

    /** 缩放比例。 */
    private Double zoom;

    /** 视口横向偏移。 */
    private Double x;

    /** 视口纵向偏移。 */
    private Double y;
}
