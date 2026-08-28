package cn.nihility.rbac.approval.service;

import cn.nihility.rbac.approval.dto.ApprovalSwitchVO;
import java.util.List;

/**
 * 主数据审批开关业务接口。
 */
public interface ApprovalSwitchService {

    /**
     * 查询全部主数据审批开关。
     *
     * @return 四类业务对象的审批开关
     */
    List<ApprovalSwitchVO> listAll();

    /**
     * 判断指定业务对象是否启用审批。
     *
     * @param bizType 业务对象类型
     * @return 是否启用审批
     */
    boolean isEnabled(String bizType);

    /**
     * 修改指定业务对象的审批开关。
     *
     * @param bizType 业务对象类型
     * @param enabled 是否启用
     * @return 修改后的审批开关
     */
    ApprovalSwitchVO update(String bizType, boolean enabled);
}
