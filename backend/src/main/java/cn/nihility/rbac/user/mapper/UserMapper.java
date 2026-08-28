package cn.nihility.rbac.user.mapper;

import cn.nihility.rbac.user.dto.UserVO;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.common.mapper.VersionedBaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，不在此处
 * 编写 SQL；{@link #countByColumnValue}、{@link #selectUserPage}、
 * {@link #selectSyncPullPage}、{@link #selectIdsByAttrCondition} 是例外，SQL 写在
 * {@code resources/mybatis/mapper/UserMapper.xml} 里。
 */
@Mapper
public interface UserMapper extends VersionedBaseMapper<UserEntity> {

    /**
     * 统计未被逻辑删除的用户中，指定列等于给定值的记录数，供"表单字段定义"驱动的
     * 非锁定字段唯一性校验使用（如 idCard）。{@code column} 只接受调用方从
     * {@code tab_metadata_field} 目录解析得到、并经白名单校验的合法列名。
     *
     * @param column    目标列名，仅接受白名单内的合法列名
     * @param value     待比对的值
     * @param excludeId 需要排除的自身 id，创建场景传 {@code null}
     * @return 命中的记录数
     */
    int countByColumnValue(@Param("column") String column, @Param("value") String value,
            @Param("excludeId") Long excludeId);

    /**
     * 按姓名/手机号/身份证号模糊搜索条件分页查询未被逻辑删除的用户，受限时（
     * {@code allowedOrgIds} 非 {@code null}）追加"存在至少一条未删除、所属组织落在
     * 管辖范围内的任职记录"过滤条件（user-org-scope-data-permission change design.md
     * Decision 2/4）。
     *
     * @param page                   分页参数
     * @param name                   姓名模糊搜索条件，可为空
     * @param mobile                 手机号模糊搜索条件，可为空
     * @param idCard                 身份证号模糊搜索条件，可为空
     * @param allowedOrgIds          管辖组织 id 全集，{@code null} 表示不受限制；非
     *                               {@code null}（哪怕是空集合）表示受限
     * @param deletedStatus          {@code tab_user} 的逻辑删除状态字面量（{@code UserStatus.DELETED}）
     * @param positionDeletedStatus  {@code tab_user_position} 的逻辑删除状态字面量（{@code PositionStatus.DELETED}）
     * @return 分页结果
     */
    IPage<UserVO> selectUserPage(IPage<?> page, @Param("name") String name, @Param("mobile") String mobile,
            @Param("idCard") String idCard, @Param("allowedOrgIds") Set<Long> allowedOrgIds,
            @Param("deletedStatus") int deletedStatus, @Param("positionDeletedStatus") int positionDeletedStatus);

    /**
     * 按管辖组织范围统计未被逻辑删除的用户总数，追加"存在至少一条未删除、所属组织落在
     * 管辖范围内的任职记录"过滤条件，与 {@link #selectUserPage} 共享同一条
     * {@code EXISTS tab_user_position} 判断逻辑，但各自独立维护 SQL（一个是分页查询，
     * 一个是纯计数，不共用同一条语句）（scope-dashboard-stats-by-admin-org change
     * design.md Decision 2）。
     *
     * @param allowedOrgIds         管辖组织 id 全集，{@code null} 表示不受限制；非
     *                              {@code null}（哪怕是空集合）表示受限
     * @param deletedStatus         {@code tab_user} 的逻辑删除状态字面量（{@code UserStatus.DELETED}）
     * @param positionDeletedStatus {@code tab_user_position} 的逻辑删除状态字面量（{@code PositionStatus.DELETED}）
     * @return 命中的用户总数
     */
    int countUsersInScope(@Param("allowedOrgIds") Set<Long> allowedOrgIds, @Param("deletedStatus") int deletedStatus,
            @Param("positionDeletedStatus") int positionDeletedStatus);

    /**
     * 分页拉取用户当前数据，不过滤 {@code status}（停用/已删除记录原样返回），按
     * {@code update_time ASC, id ASC} 排序，组织范围过滤通过存在至少一条未删除且所属组织
     * 落在允许范围内的任职记录下推到 SQL（app-sync-drop-changelog change design.md
     * Decision 1/2/4）。
     *
     * @param offset         偏移量
     * @param limit          每页大小
     * @param updateTimeFrom 更新时间范围起点（含），可为空
     * @param updateTimeTo   更新时间范围终点（含），可为空
     * @param ids            主键 id 列表精确过滤，{@code null} 表示不过滤
     * @param codes          用户编码列表精确过滤，{@code null} 表示不过滤
     * @param mobile         手机号精确过滤，{@code null}/空串表示不过滤
     * @param allowedOrgIds  组织范围过滤下推的允许组织 id 全集，{@code null} 表示不限制
     * @return 查询结果列表
     */
    List<UserEntity> selectSyncPullPage(@Param("offset") int offset, @Param("limit") int limit,
            @Param("updateTimeFrom") LocalDateTime updateTimeFrom, @Param("updateTimeTo") LocalDateTime updateTimeTo,
            @Param("ids") List<Long> ids, @Param("codes") List<String> codes, @Param("mobile") String mobile,
            @Param("allowedOrgIds") Set<Long> allowedOrgIds);

    /**
     * 按动态列名与运算符匹配未被逻辑删除的用户 id，供"应用访问授权"策略的用户属性条件
     * 执行匹配使用（app-access-authorization change design.md Decision 1/Risks）。
     * {@code column} 只接受调用方从 {@code tab_metadata_field} 目录解析得到、并经白名单
     * 校验（{@code table_name='tab_user'} 且列名仅含字母数字下划线）的合法列名，不接受
     * 任意字符串拼接，避免 SQL 注入。
     *
     * @param column       目标列名，仅接受白名单内的合法列名
     * @param operator     运算符：{@code EQ}/{@code NE}/{@code IN}
     * @param singleValue  {@code EQ}/{@code NE} 时的比较值，{@code IN} 时忽略
     * @param values       {@code IN} 时的一组比较值，{@code EQ}/{@code NE} 时忽略
     * @return 命中的用户 id 列表
     */
    List<Long> selectIdsByAttrCondition(@Param("column") String column, @Param("operator") String operator,
            @Param("singleValue") String singleValue, @Param("values") List<String> values);

    /**
     * 按 {@code id} 升序游标式批量查询用户当前数据，供对账摘要接口流式扫描全部可见记录使用
     * （app-sync-changelog-pull change design.md Decision 10）。
     *
     * @param lastId        上一批最后一条记录的 id，{@code null} 表示从头开始
     * @param batchSize     本批最多查询的记录数
     * @param allowedOrgIds 组织范围过滤下推的允许组织 id 全集，{@code null} 表示不限制
     * @return 本批查询结果，按 id 升序排列
     */
    List<UserEntity> selectDigestBatch(@Param("lastId") Long lastId, @Param("batchSize") int batchSize,
            @Param("allowedOrgIds") Set<Long> allowedOrgIds);
}
