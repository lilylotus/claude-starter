package cn.nihility.rbac.app.authconfig.mapper;

import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingRow;
import cn.nihility.rbac.app.authconfig.entity.AppUserinfoFieldMappingEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 应用用户信息响应字段映射 MyBatis-Plus 数据访问接口。单表 CRUD 直接复用
 * {@link BaseMapper}；联表查询（携带本地字段名称/编码）走自定义方法 +
 * {@code AppUserinfoFieldMappingMapper.xml}（对齐 {@code AppSyncFieldMappingMapper}
 * 的既有约定：多表 JOIN 查询写在 MyBatis XML）。也供 {@code sso.support}
 * 模块直接注入，用于运行时解析用户信息响应属性（跨模块只读查询直接复用对方 Mapper）。
 */
@Mapper
public interface AppUserinfoFieldMappingMapper extends BaseMapper<AppUserinfoFieldMappingEntity> {

    /**
     * 按应用 id 查询用户信息字段映射列表，携带 LEFT JOIN {@code tab_metadata_field} 得到
     * 的本地字段名称/编码（关联字段为空时回填固定的"用户ID"伪字段展示信息），按 {@code id}
     * 升序排列（整体替换语义下 id 升序即保存时的提交顺序）。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @return 字段映射行列表，该应用无任何记录时返回空列表
     */
    List<AppUserinfoFieldMappingRow> selectByAppRefId(@Param("appRefId") Long appRefId);
}
