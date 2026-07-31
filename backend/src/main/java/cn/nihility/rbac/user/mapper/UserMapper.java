package cn.nihility.rbac.user.mapper;

import cn.nihility.rbac.user.dto.UserVO;
import cn.nihility.rbac.user.entity.UserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，不在此处
 * 编写 SQL；{@link #countByColumnValue}、{@link #selectUserPage} 是例外，SQL 写在
 * {@code resources/mybatis/mapper/UserMapper.xml} 里。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

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
}
