package cn.nihility.rbac.workflow.mapper;

import cn.nihility.rbac.workflow.entity.FormVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表单版本数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}。
 */
@Mapper
public interface FormVersionMapper extends BaseMapper<FormVersionEntity> {
}
