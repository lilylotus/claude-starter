package cn.nihility.rbac.appaccess.policy.service;

import cn.nihility.rbac.appaccess.policy.dto.PolicyCreateRequest;
import cn.nihility.rbac.appaccess.policy.dto.PolicyQueryRequest;
import cn.nihility.rbac.appaccess.policy.dto.PolicyUpdateRequest;
import cn.nihility.rbac.appaccess.policy.dto.PolicyVO;
import cn.nihility.rbac.common.result.PageResult;

/**
 * 策略规则业务逻辑接口：新增/编辑（组织范围/用户属性条件/目标应用整体替换语义）、
 * 分页查询、详情、启用/停用、删除（级联清理该策略产生的策略授权记录）。
 */
public interface PolicyService {

    /**
     * 分页查询策略规则。
     *
     * @param request 查询参数
     * @return 分页结果
     */
    PageResult<PolicyVO> page(PolicyQueryRequest request);

    /**
     * 查询策略规则详情，含组织范围/用户属性条件/目标应用完整回显。
     *
     * @param id 策略 id
     * @return 策略规则详情
     */
    PolicyVO detail(Long id);

    /**
     * 新增策略规则。
     *
     * @param request 新增请求
     * @return 新增后的策略规则详情
     */
    PolicyVO create(PolicyCreateRequest request);

    /**
     * 编辑策略规则，组织范围/用户属性条件/目标应用均为整体替换语义（先删后插）。
     *
     * @param id      策略 id
     * @param request 编辑请求
     * @return 编辑后的策略规则详情
     */
    PolicyVO update(Long id, PolicyUpdateRequest request);

    /**
     * 启用策略规则：其产生的策略授权记录立即重新计入最终生效权限，无需重新执行。
     *
     * @param id 策略 id
     * @return 更新后的策略规则详情
     */
    PolicyVO enable(Long id);

    /**
     * 停用策略规则：其产生的策略授权记录立即不再计入最终生效权限，无需清空记录。
     *
     * @param id 策略 id
     * @return 更新后的策略规则详情
     */
    PolicyVO disable(Long id);

    /**
     * 删除策略规则，级联删除该策略已产生的全部策略授权记录（同一事务），不留下指向已删除
     * 策略的孤儿记录。
     *
     * @param id 策略 id
     */
    void delete(Long id);
}
