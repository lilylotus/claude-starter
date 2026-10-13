package cn.nihility.rbac.abac.mapstruct;

import cn.nihility.rbac.abac.dto.AbacPolicyVO;
import cn.nihility.rbac.abac.entity.AbacPolicyEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * ABAC 策略规则实体与视图对象之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态单例调用。只负责 {@link AbacPolicyEntity} 自身字段的转换，
 * 目标资源编码/用户属性条件/时间窗口条件等列表字段来自独立的联表查询，转换后由调用方
 * 自行填充。
 */
@Mapper
public interface AbacPolicyConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    AbacPolicyConvert INSTANCE = Mappers.getMapper(AbacPolicyConvert.class);

    /**
     * 实体转视图对象（不含目标资源编码/用户属性条件/时间窗口条件）。
     *
     * @param entity 策略规则实体
     * @return 视图对象
     */
    @Mapping(target = "resourceCodes", ignore = true)
    @Mapping(target = "userAttrs", ignore = true)
    @Mapping(target = "timeWindows", ignore = true)
    AbacPolicyVO toVO(AbacPolicyEntity entity);
}
