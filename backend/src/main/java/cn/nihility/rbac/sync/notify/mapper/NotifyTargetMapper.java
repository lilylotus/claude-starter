package cn.nihility.rbac.sync.notify.mapper;

import cn.nihility.rbac.sync.notify.dto.NotifyTargetRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 通知目标应用查询数据访问接口：联表 {@code tab_app_config}/{@code tab_app}/
 * {@code tab_app_sync_domain_config} 查询"某数据类型下 {@code syncMode=NOTIFY} 且该域
 * {@code syncEnabled=true} 的启用中应用"列表，SQL 写在 {@code NotifyTargetMapper.xml} 里
 * （仓库既有约定：多表 JOIN 查询写在 MyBatis XML，不在 Java 侧循环查询后手工合并）。
 */
@Mapper
public interface NotifyTargetMapper {

    /**
     * 查询指定数据类型下需要通知的应用列表。
     *
     * @param dataType 数据类型
     * @return 通知目标应用列表
     */
    List<NotifyTargetRow> selectNotifyTargets(@Param("dataType") String dataType);
}
