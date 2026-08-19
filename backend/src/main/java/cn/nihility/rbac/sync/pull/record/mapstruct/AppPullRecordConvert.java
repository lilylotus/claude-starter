package cn.nihility.rbac.sync.pull.record.mapstruct;

import cn.nihility.rbac.sync.pull.record.dto.AppPullRecordVO;
import cn.nihility.rbac.sync.pull.record.entity.AppPullRecordEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 应用拉取变更数据请求记录实体与视图对象之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态单例调用。
 */
@Mapper
public interface AppPullRecordConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    AppPullRecordConvert INSTANCE = Mappers.getMapper(AppPullRecordConvert.class);

    /**
     * 实体转视图对象。
     *
     * @param entity 应用拉取变更数据请求记录实体
     * @return 应用拉取变更数据请求记录视图对象
     */
    AppPullRecordVO toVO(AppPullRecordEntity entity);

    /**
     * 实体列表批量转视图对象列表。
     *
     * @param entities 应用拉取变更数据请求记录实体列表
     * @return 应用拉取变更数据请求记录视图对象列表
     */
    List<AppPullRecordVO> toVOList(List<AppPullRecordEntity> entities);
}
