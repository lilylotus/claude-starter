package cn.nihility.rbac.user.service.support;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.formfield.support.DynamicFieldValidator;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code bizType=POSITION} 动态字段（必填/正则/唯一性）校验的共享组件，供独立任职管理入口
 * （{@code PositionServiceImpl}，请求对象为 {@code PositionCreateRequest}/
 * {@code PositionUpdateRequest}）与用户管理内嵌任职子表单（{@code UserServiceImpl}，请求对象
 * 为 {@code UserPositionRequest}）共用，避免两处各自实现一套相同的校验逻辑。请求对象只要
 * 属性名与 {@code bizType=POSITION} 字段定义绑定的列名（转驼峰后）一致即可复用本组件，
 * 与具体请求 DTO 类型无关。
 */
@Component
@RequiredArgsConstructor
public class PositionDynamicFieldSupport {

    /**
     * {@code bizType=POSITION} 下允许被动态字段唯一性校验拼进 {@code ${column}} 的
     * 列名白名单，取自 {@code tab_metadata_field} 目录里 POSITION 的原有可配置列 +
     * {@code ext1}..{@code ext10}（design.md Decision 3/8）。任职管理没有承重字段，
     * 全部字段定义都会经过这条动态校验管线。
     */
    private static final Set<String> ALLOWED_DYNAMIC_COLUMNS = Set.of(
            "position_address", "position_phone", "show_order", "remark",
            "ext1", "ext2", "ext3", "ext4", "ext5", "ext6", "ext7", "ext8", "ext9", "ext10");

    /** 表单字段定义业务逻辑接口，用于驱动非锁定字段的必填/正则/唯一性校验。 */
    private final FormFieldDefinitionService formFieldDefinitionService;

    /** 用户任职记录数据访问接口，仅用于唯一性校验时统计命中记录数。 */
    private final UserPositionMapper userPositionMapper;

    /**
     * 对适用于当前场景的 {@code bizType=POSITION} 字段定义执行必填、正则、唯一性校验
     * （design.md Decision 9）。任职管理没有承重字段，
     * {@code formFieldDefinitionService.listActiveByBizType} 返回的全部定义都会参与本方法。
     *
     * @param request   任职记录创建或更新请求，按 {@code fieldCode} 反射读取字段值
     * @param creating  是否为新增场景
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 {@code null}
     */
    public void validate(Object request, boolean creating, Long excludeId) {
        List<FormFieldDefinitionVO> definitions =
                formFieldDefinitionService.listActiveByBizType(FormFieldBizType.POSITION);
        DynamicFieldValidator.validate(definitions, request, creating, (column, value) -> {
            if (!ALLOWED_DYNAMIC_COLUMNS.contains(column)) {
                throw new BusinessException("非法的动态字段列名：" + column);
            }
            return userPositionMapper.countByColumnValue(column, value, excludeId);
        });
    }
}
