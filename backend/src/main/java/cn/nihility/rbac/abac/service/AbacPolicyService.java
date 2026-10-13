package cn.nihility.rbac.abac.service;

import cn.nihility.rbac.abac.dto.AbacPolicyCreateRequest;
import cn.nihility.rbac.abac.dto.AbacPolicyQueryRequest;
import cn.nihility.rbac.abac.dto.AbacPolicyUpdateRequest;
import cn.nihility.rbac.abac.dto.AbacPolicyVO;
import cn.nihility.rbac.common.result.PageResult;

/**
 * ABAC 策略规则业务逻辑接口：新增、编辑、分页查询、详情、启用/停用、删除。
 */
public interface AbacPolicyService {

    /**
     * 分页查询策略规则。
     *
     * @param request 分页查询参数
     * @return 策略规则的分页结果
     */
    PageResult<AbacPolicyVO> page(AbacPolicyQueryRequest request);

    /**
     * 查询策略规则详情。
     *
     * @param id 策略 id
     * @return 策略规则详情
     */
    AbacPolicyVO detail(Long id);

    /**
     * 新增策略规则。
     *
     * @param request 新增请求
     * @return 新增后的策略规则详情
     */
    AbacPolicyVO create(AbacPolicyCreateRequest request);

    /**
     * 编辑策略规则，目标资源编码/用户属性条件/时间窗口条件均为整体替换语义。
     *
     * @param id      策略 id
     * @param request 编辑请求
     * @return 编辑后的策略规则详情
     */
    AbacPolicyVO update(Long id, AbacPolicyUpdateRequest request);

    /**
     * 启用策略规则。
     *
     * @param id 策略 id
     * @return 更新后的策略规则详情
     */
    AbacPolicyVO enable(Long id);

    /**
     * 停用策略规则。
     *
     * @param id 策略 id
     * @return 更新后的策略规则详情
     */
    AbacPolicyVO disable(Long id);

    /**
     * 删除策略规则（物理删除，级联清理目标资源编码/用户属性条件/时间窗口条件）。
     *
     * @param id 策略 id
     */
    void delete(Long id);
}
