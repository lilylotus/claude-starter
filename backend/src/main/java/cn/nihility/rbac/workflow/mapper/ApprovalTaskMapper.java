package cn.nihility.rbac.workflow.mapper;

import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审批任务数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}；"我的待办/已办"分页查询需要
 * 关联 {@code tab_wf_process_instance}（业务对象类型过滤）与 {@code tab_wf_approval_record}
 * （已办去重取最新一条），单表 Lambda 构造器无法表达，SQL 写在
 * {@code mybatis/mapper/ApprovalTaskMapper.xml}（production-approval-lifecycle change
 * tasks.md 6.9：查询/排序/分页必须下推到数据库层，不允许加载全量到 Java 后内存过滤分页）。
 */
@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTaskEntity> {

    /**
     * 待办任务分页查询：在候选任务 id 集合（assignee 精确匹配 + 候选人明细用户/角色维度匹配，
     * 已在 Service 层完成解析）基础上，按业务对象类型过滤，数据库层按
     * {@code create_time DESC, id DESC} 稳定排序后 {@code LIMIT/OFFSET} 分页。
     *
     * @param taskIds      候选任务 id 集合，不能为空
     * @param businessType 业务对象类型过滤，可为空表示不过滤
     * @param offset       偏移量
     * @param limit        每页大小
     * @return 已排序、已分页的审批任务实体列表
     */
    List<ApprovalTaskEntity> selectTodoPage(
            @Param("taskIds") Collection<Long> taskIds,
            @Param("businessType") String businessType,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 已办任务分页查询：以操作人在 {@code tab_wf_approval_record} 命中的、动作类型属于
     * {@code actions} 的最新一条记录为准（同一任务可能存在多条不同动作的记录，如先委派后归还
     * 再完成，只取最新一条，语义与原内存实现的 {@code distinct(taskId)} 保持一致），按业务对象
     * 类型过滤，数据库层按记录发生时间 {@code create_time DESC, id DESC} 稳定排序后
     * {@code LIMIT/OFFSET} 分页。
     *
     * @param operatorId   操作人（当前用户）id
     * @param actions      计入"已办"的动作类型集合
     * @param businessType 业务对象类型过滤，可为空表示不过滤
     * @param offset       偏移量
     * @param limit        每页大小
     * @return 已排序、已分页、已去重的审批任务实体列表
     */
    List<ApprovalTaskEntity> selectDonePage(
            @Param("operatorId") Long operatorId,
            @Param("actions") Collection<String> actions,
            @Param("businessType") String businessType,
            @Param("offset") int offset,
            @Param("limit") int limit);
}
