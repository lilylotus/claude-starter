package cn.nihility.rbac.app.sync.mapstruct;

import cn.nihility.rbac.app.sync.dto.AppSyncDomainConfigVO;
import cn.nihility.rbac.app.sync.entity.AppSyncDomainConfigEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 应用同步数据域配置实体与视图对象之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface AppSyncDomainConfigConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    AppSyncDomainConfigConvert INSTANCE = Mappers.getMapper(AppSyncDomainConfigConvert.class);

    /**
     * 实体转视图对象。
     *
     * @param entity 应用同步数据域配置实体
     * @return 应用同步数据域配置视图对象
     */
    AppSyncDomainConfigVO toVO(AppSyncDomainConfigEntity entity);

    /**
     * 实体列表批量转视图对象列表。
     *
     * @param entities 应用同步数据域配置实体列表
     * @return 应用同步数据域配置视图对象列表
     */
    List<AppSyncDomainConfigVO> toVOList(List<AppSyncDomainConfigEntity> entities);
}
