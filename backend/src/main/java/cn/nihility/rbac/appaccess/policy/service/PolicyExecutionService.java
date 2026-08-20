package cn.nihility.rbac.appaccess.policy.service;

import cn.nihility.rbac.appaccess.policy.dto.PolicyVO;

/**
 * 策略规则手动执行业务逻辑接口（spec.md"策略规则的手动执行"需求）。
 */
public interface PolicyExecutionService {

    /**
     * 执行指定策略：按组织范围/用户属性条件匹配命中用户，与目标应用集合做笛卡尔积，整体
     * 重建该策略产生的策略授权记录（先删后插），SHALL NOT 影响任何人工例外记录，同时记录
     * 本次执行时间与执行人。
     *
     * @param policyId 策略 id
     * @return 执行后的策略规则详情（含最新 {@code lastExecTime}/{@code lastExecBy}）
     */
    PolicyVO execute(Long policyId);
}
