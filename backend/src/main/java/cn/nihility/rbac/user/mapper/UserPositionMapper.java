package cn.nihility.rbac.user.mapper;

import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户任职记录数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}；按组织分页查询、
 * 按 id 查询详情需要关联 {@code tab_user}、{@code tab_org} 两张表回填姓名/组织名称，
 * SQL 写在 {@code resources/mybatis/mapper/UserPositionMapper.xml} 里，用单条 JOIN
 * 完成，不在应用层拆成多次查询再拼装。
 */
@Mapper
public interface UserPositionMapper extends BaseMapper<UserPositionEntity> {

    /**
     * 按所属组织 id 分页查询任职记录，关联回填所属用户姓名、所属组织名称。
     *
     * @param page          分页参数
     * @param orgId         所属组织 id
     * @param deletedStatus 逻辑删除状态值，用于在 SQL 里排除已删除记录
     * @return 分页结果，{@code records} 已包含 {@code userName}/{@code orgName}
     */
    IPage<PositionVO> selectPositionPage(IPage<?> page, @Param("orgId") Long orgId,
            @Param("deletedStatus") int deletedStatus);

    /**
     * 按 id 查询任职记录详情，关联回填所属用户姓名、所属组织名称。
     *
     * @param id            任职记录 id
     * @param deletedStatus 逻辑删除状态值，用于在 SQL 里排除已删除记录
     * @return 任职记录详情，不存在或已被逻辑删除时返回 {@code null}
     */
    PositionVO selectPositionDetail(@Param("id") Long id, @Param("deletedStatus") int deletedStatus);

    /**
     * 统计未被逻辑删除的任职记录中，指定列等于给定值的记录数，供"表单字段定义"驱动的
     * 非锁定字段唯一性校验使用。{@code column} 只接受调用方从 {@code tab_metadata_field}
     * 目录解析得到、并经白名单校验的合法列名。
     *
     * @param column    目标列名，仅接受白名单内的合法列名
     * @param value     待比对的值
     * @param excludeId 需要排除的自身 id，创建场景传 {@code null}
     * @return 命中的记录数
     */
    int countByColumnValue(@Param("column") String column, @Param("value") String value,
            @Param("excludeId") Long excludeId);
}
