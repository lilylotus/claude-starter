package cn.nihility.rbac.workflow.mapper;

import cn.nihility.rbac.workflow.entity.ProcessBindingEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务绑定数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}。
 */
@Mapper
public interface ProcessBindingMapper extends BaseMapper<ProcessBindingEntity> {
}
