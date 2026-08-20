package cn.nihility.rbac.appaccess.override.mapper;

import cn.nihility.rbac.appaccess.override.dto.ManualOverrideVO;
import cn.nihility.rbac.appaccess.override.entity.ManualOverrideEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 人工例外数据访问接口。单表 CRUD（新增/更新/删除/按 {@code userId+appId} 查询已有记录）
 * 直接复用 {@link BaseMapper}；分页查询需要关联 {@code tab_user}/{@code tab_app} 回填
 * 用户姓名/应用名称，SQL 写在 {@code resources/mybatis/mapper/ManualOverrideMapper.xml}
 * 里，用单条 JOIN 完成。也供
 * {@code cn.nihility.rbac.appaccess.support.impl.AppAccessEffectivePermissionServiceImpl}
 * 直接注入使用。
 */
@Mapper
public interface ManualOverrideMapper extends BaseMapper<ManualOverrideEntity> {

    /**
     * 分页查询人工例外，支持按用户 id、应用 id、例外类型过滤，均可选，关联回填用户姓名/
     * 应用名称。
     *
     * @param page         分页参数
     * @param userId       用户 id，精确匹配，可为空
     * @param appId        应用 id，精确匹配，可为空
     * @param overrideType 例外类型，精确匹配，可为空
     * @return 分页结果
     */
    IPage<ManualOverrideVO> selectOverridePage(IPage<?> page, @Param("userId") Long userId,
            @Param("appId") Long appId, @Param("overrideType") String overrideType);

    /**
     * 按主键 id 查询人工例外详情，关联回填用户姓名/应用名称，供新增/更新接口返回完整视图
     * 对象使用。
     *
     * @param id 主键 id
     * @return 人工例外视图对象，不存在或关联用户/应用已被逻辑删除时返回 {@code null}
     */
    ManualOverrideVO selectVOById(@Param("id") Long id);
}
