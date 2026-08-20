package cn.nihility.rbac.appaccess.policy.mapper;

import cn.nihility.rbac.appaccess.policy.dto.PolicyOrgScopeVO;
import cn.nihility.rbac.appaccess.policy.entity.PolicyOrgScopeEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 策略组织范围条件数据访问接口。单表 CRUD（整体替换用的批量插入/按 {@code policyId}
 * 删除）直接复用 {@link BaseMapper}；按策略 id 查询需要关联 {@code tab_org} 回填组织
 * 名称，SQL 写在 {@code resources/mybatis/mapper/PolicyOrgScopeMapper.xml} 里，用单条
 * JOIN 完成（参照 {@code cn.nihility.rbac.app.sync.mapper.AppSyncOrgScopeMapper} 的既有
 * 写法）。
 */
@Mapper
public interface PolicyOrgScopeMapper extends BaseMapper<PolicyOrgScopeEntity> {

    /**
     * 按策略 id 查询其全部组织范围条件，关联回填组织名称；组织若已被逻辑删除则不返回
     * （INNER JOIN 语义，脏关联数据没有展示价值）。
     *
     * @param policyId 策略 id
     * @return 组织范围条件视图对象列表
     */
    List<PolicyOrgScopeVO> selectByPolicyId(@Param("policyId") Long policyId);
}
