package cn.nihility.rbac.app.mapper;

import cn.nihility.rbac.app.entity.AppConfigEntity;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用对外接口凭证配置 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不需要自定义方法。
 */
@Mapper
public interface AppConfigMapper extends BaseMapper<AppConfigEntity> {

    /** 原子递增应用级同步配置纪元。 */
    default int incrementConfigEpoch(Long appRefId, String updateBy, LocalDateTime updateTime) {
        LambdaUpdateWrapper<AppConfigEntity> wrapper = Wrappers.<AppConfigEntity>lambdaUpdate()
                .eq(AppConfigEntity::getAppRefId, appRefId)
                .setSql("config_epoch = config_epoch + 1")
                .set(AppConfigEntity::getUpdateBy, updateBy)
                .set(AppConfigEntity::getUpdateTime, updateTime);
        return update(null, wrapper);
    }

    /** 更新同步总配置并在同一条 SQL 中原子递增配置纪元。 */
    default int updateSyncConfigAndIncrementEpoch(AppConfigEntity entity) {
        LambdaUpdateWrapper<AppConfigEntity> wrapper = Wrappers.<AppConfigEntity>lambdaUpdate()
                .eq(AppConfigEntity::getAppRefId, entity.getAppRefId())
                .set(AppConfigEntity::getSyncMode, entity.getSyncMode())
                .set(AppConfigEntity::getNotifyUrl, entity.getNotifyUrl())
                .set(AppConfigEntity::getNotifyParams, entity.getNotifyParams())
                .set(AppConfigEntity::getNeedSign, entity.getNeedSign())
                .set(AppConfigEntity::getSyncMasterEnabled, entity.getSyncMasterEnabled())
                .set(AppConfigEntity::getUpdateBy, entity.getUpdateBy())
                .set(AppConfigEntity::getUpdateTime, entity.getUpdateTime())
                .setSql("config_epoch = config_epoch + 1");
        return update(null, wrapper);
    }
}
