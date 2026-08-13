package cn.nihility.rbac.identity.upstream.mapstruct;

import cn.nihility.rbac.identity.upstream.dto.UpstreamSourceVO;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSourceEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 上游数据源实体与视图对象之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态创建单例调用。{@code apiAuthHeaders}/{@code dbPassword} 是敏感
 * 字段，不映射到 {@link UpstreamSourceVO}（VO 上另有只返回 key 列表的
 * {@code apiAuthHeaderKeys}，由服务层单独解析填充）。
 */
@Mapper
public interface UpstreamSourceConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    UpstreamSourceConvert INSTANCE = Mappers.getMapper(UpstreamSourceConvert.class);

    /**
     * 实体转视图对象，敏感字段（{@code apiAuthHeaders}/{@code dbPassword}）不映射，
     * {@code apiAuthHeaderKeys} 由调用方解析原始 JSON 后单独填充。
     *
     * @param entity 上游数据源实体
     * @return 上游数据源视图对象
     */
    @Mapping(target = "apiAuthHeaderKeys", ignore = true)
    UpstreamSourceVO toVO(UpstreamSourceEntity entity);

    /**
     * 实体列表批量转视图对象列表。
     *
     * @param entities 上游数据源实体列表
     * @return 上游数据源视图对象列表
     */
    List<UpstreamSourceVO> toVOList(List<UpstreamSourceEntity> entities);
}
