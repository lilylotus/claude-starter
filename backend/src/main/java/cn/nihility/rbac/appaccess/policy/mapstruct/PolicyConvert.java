package cn.nihility.rbac.appaccess.policy.mapstruct;

import cn.nihility.rbac.appaccess.policy.dto.PolicyVO;
import cn.nihility.rbac.appaccess.policy.entity.PolicyEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 策略规则实体与视图对象之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态单例调用。只负责 {@link PolicyEntity} 自身字段的转换，
 * 组织范围/用户属性条件/目标应用/请求控制条件（浏览器白名单/IP 白名单）等列表字段与
 * {@code pendingReExecute} 标记来自独立的联表查询与服务层计算，转换后由调用方自行填充。
 */
@Mapper
public interface PolicyConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    PolicyConvert INSTANCE = Mappers.getMapper(PolicyConvert.class);

    /**
     * 实体转视图对象（不含组织范围/用户属性条件/目标应用/请求控制条件/
     * {@code pendingReExecute}）。
     *
     * @param entity 策略规则实体
     * @return 视图对象
     */
    @Mapping(target = "orgScopes", ignore = true)
    @Mapping(target = "userAttrs", ignore = true)
    @Mapping(target = "targetApps", ignore = true)
    @Mapping(target = "browserRules", ignore = true)
    @Mapping(target = "ipRules", ignore = true)
    @Mapping(target = "pendingReExecute", ignore = true)
    PolicyVO toVO(PolicyEntity entity);
}
