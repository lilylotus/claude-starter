package cn.nihility.rbac.workflow.dslv2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 画布节点坐标，仅前端往返使用，不参与编译（design.md Decision 3 DSL v2 示例）。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionDsl {

    /** 横坐标。 */
    private Double x;

    /** 纵坐标。 */
    private Double y;
}
