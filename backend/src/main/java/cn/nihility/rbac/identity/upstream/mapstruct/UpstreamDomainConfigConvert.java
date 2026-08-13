package cn.nihility.rbac.identity.upstream.mapstruct;

import cn.nihility.rbac.identity.upstream.dto.UpstreamDomainConfigVO;
import cn.nihility.rbac.identity.upstream.entity.UpstreamDomainConfigEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 上游数据源数据域配置实体与视图对象之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface UpstreamDomainConfigConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    UpstreamDomainConfigConvert INSTANCE = Mappers.getMapper(UpstreamDomainConfigConvert.class);

    /**
     * 实体转视图对象，字段名完全一致，直接同名复制。
     *
     * @param entity 数据域配置实体
     * @return 数据域配置视图对象
     */
    UpstreamDomainConfigVO toVO(UpstreamDomainConfigEntity entity);

    /**
     * 实体列表批量转视图对象列表。
     *
     * @param entities 数据域配置实体列表
     * @return 数据域配置视图对象列表
     */
    List<UpstreamDomainConfigVO> toVOList(List<UpstreamDomainConfigEntity> entities);
}
