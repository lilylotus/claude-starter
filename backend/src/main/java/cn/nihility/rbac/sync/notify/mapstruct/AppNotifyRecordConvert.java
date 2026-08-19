package cn.nihility.rbac.sync.notify.mapstruct;

import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordVO;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 应用通知发送记录实体与视图对象之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态单例调用。
 */
@Mapper
public interface AppNotifyRecordConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    AppNotifyRecordConvert INSTANCE = Mappers.getMapper(AppNotifyRecordConvert.class);

    /**
     * 实体转视图对象。
     *
     * @param entity 应用通知发送记录实体
     * @return 应用通知发送记录视图对象
     */
    AppNotifyRecordVO toVO(AppNotifyRecordEntity entity);

    /**
     * 实体列表批量转视图对象列表。
     *
     * @param entities 应用通知发送记录实体列表
     * @return 应用通知发送记录视图对象列表
     */
    List<AppNotifyRecordVO> toVOList(List<AppNotifyRecordEntity> entities);
}
