package cn.nihility.rbac.approval.mapper;

import cn.nihility.rbac.approval.entity.ApprovalRequestEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批申请 MyBatis-Plus 数据访问接口。
 */
@Mapper
public interface ApprovalRequestMapper extends BaseMapper<ApprovalRequestEntity> {
}
