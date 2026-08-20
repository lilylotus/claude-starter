package cn.nihility.rbac.appaccess.support.mapper;

import cn.nihility.rbac.appaccess.support.dto.EffectiveRawRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 最终生效权限查询专用数据访问接口，不对应单一实体，只提供
 * {@code AppAccessEffectivePermissionServiceImpl} 组装"按用户/按应用"最终生效权限列表
 * 所需的联表原始行查询，SQL 写在
 * {@code resources/mybatis/mapper/AppAccessEffectiveMapper.xml} 里（design.md
 * Decision 3；策略授权记录与人工例外物理隔离在两张表，JOIN 在 SQL 完成，多条策略名称的
 * 分组聚合在服务层完成，避免使用 MySQL 5.7 不支持的窗口函数或厂商专属的
 * {@code GROUP_CONCAT}）。
 */
@Mapper
public interface AppAccessEffectiveMapper {

    /**
     * 查询指定用户当前由启用中策略产生的策略授权记录，关联应用名称与（若存在）该
     * 用户+应用的人工例外类型。
     *
     * @param userId 用户 id
     * @return 联表原始行列表，一行对应一条"应用+命中策略"
     */
    List<EffectiveRawRow> selectActiveGrantRowsByUser(@Param("userId") Long userId);

    /**
     * 查询指定用户的人工例外中，未被任何启用中策略覆盖的应用（即该应用没有对应的策略授权
     * 记录），关联应用名称。
     *
     * @param userId 用户 id
     * @return 联表原始行列表，{@code policyName} 恒为空
     */
    List<EffectiveRawRow> selectManualOnlyRowsByUser(@Param("userId") Long userId);

    /**
     * 查询指定应用当前由启用中策略产生的策略授权记录，关联用户姓名与（若存在）该
     * 用户+应用的人工例外类型。
     *
     * @param appId 应用 id
     * @return 联表原始行列表，一行对应一条"用户+命中策略"
     */
    List<EffectiveRawRow> selectActiveGrantRowsByApp(@Param("appId") Long appId);

    /**
     * 查询指定应用的人工例外中，未被任何启用中策略覆盖的用户，关联用户姓名。
     *
     * @param appId 应用 id
     * @return 联表原始行列表，{@code policyName} 恒为空
     */
    List<EffectiveRawRow> selectManualOnlyRowsByApp(@Param("appId") Long appId);
}
