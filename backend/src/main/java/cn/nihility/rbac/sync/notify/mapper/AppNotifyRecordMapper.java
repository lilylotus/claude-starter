package cn.nihility.rbac.sync.notify.mapper;

import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用通知发送记录 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}。
 */
@Mapper
public interface AppNotifyRecordMapper extends BaseMapper<AppNotifyRecordEntity> {
}
