package cn.nihility.rbac.sync.pull.record.mapper;

import cn.nihility.rbac.sync.pull.record.dto.AppPullRecordQueryRequest;
import cn.nihility.rbac.sync.pull.record.entity.AppPullRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 应用拉取变更数据请求记录 MyBatis-Plus 数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}；
 * 管理端分页查询按应用 id 精确匹配 + 可选的时间范围过滤，SQL 写在
 * {@code resources/mybatis/mapper/AppPullRecordMapper.xml} 里（仓库既有约定）。
 */
@Mapper
public interface AppPullRecordMapper extends BaseMapper<AppPullRecordEntity> {

    /**
     * 按应用 id 精确匹配 + 可选的时间范围过滤动态分页查询拉取日志，按拉取发起时间降序排列
     * （add-app-sync-notify-pull-logs change design.md Decision 3）。
     *
     * @param page  分页参数
     * @param query 筛选参数，{@code appRefId} 必填，其余均可选
     * @return 分页结果
     */
    IPage<AppPullRecordEntity> selectPullRecordPage(IPage<?> page, @Param("query") AppPullRecordQueryRequest query);
}
