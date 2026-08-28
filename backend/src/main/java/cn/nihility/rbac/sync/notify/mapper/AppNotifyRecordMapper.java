package cn.nihility.rbac.sync.notify.mapper;

import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordQueryRequest;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 应用通知发送记录 MyBatis-Plus 数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}；
 * 管理端分页查询按应用 id 精确匹配 + 可选的通知状态精确匹配 + 可选的时间范围过滤，
 * {@link #selectDueTasks} 按状态机到期条件筛选，SQL 写在
 * {@code resources/mybatis/mapper/AppNotifyRecordMapper.xml} 里（仓库既有约定）。
 */
@Mapper
public interface AppNotifyRecordMapper extends BaseMapper<AppNotifyRecordEntity> {

    /**
     * 按应用 id 精确匹配 + 可选条件动态分页查询通知日志，按通知发起时间降序排列
     * （add-app-sync-notify-pull-logs change design.md Decision 3）。
     *
     * @param page  分页参数
     * @param query 筛选参数，{@code appRefId} 必填，其余均可选
     * @return 分页结果
     */
    IPage<AppNotifyRecordEntity> selectNotifyRecordPage(IPage<?> page,
            @Param("query") AppNotifyRecordQueryRequest query);

    /**
     * 扫描当前到期需要处理的通知任务：{@code PENDING}/{@code RETRY} 状态且到期，或
     * {@code PROCESSING} 状态且租约已超时，按主键升序取前 {@code limit} 条
     * （app-sync-changelog-pull change design.md Decision 6）。
     *
     * @param now   扫描基准时刻
     * @param limit 单轮最多返回的记录数
     * @return 到期任务列表，按 id 升序排列
     */
    List<AppNotifyRecordEntity> selectDueTasks(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
