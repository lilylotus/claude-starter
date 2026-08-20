package cn.nihility.rbac.appaccess.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code AppAccessEffectiveMapper} 各联表查询结果的通用载体 DTO（非对外 VO），每行代表
 * "用户+应用"命中的一条策略授权记录或一条人工例外记录，供
 * {@code AppAccessEffectivePermissionServiceImpl} 按 {@code appId}（按用户查询场景）或
 * {@code userId}（按应用查询场景）分组，应用 DENY 优先于 GRANT 优先于 POLICY 的合并
 * 判定规则后转换为对外的
 * {@link cn.nihility.rbac.appaccess.dto.AppAccessEffectiveItemVO}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveRawRow {

    /** 用户 id。 */
    private Long userId;

    /** 用户姓名，按应用查询场景下有值。 */
    private String userName;

    /** 应用 id。 */
    private Long appId;

    /** 应用名称，按用户查询场景下有值。 */
    private String appName;

    /** 命中的策略名称，来自策略授权记录联表查询行时有值，来自人工例外行时为空。 */
    private String policyName;

    /** 人工例外类型：GRANT/DENY，本行同时存在对应人工例外记录时有值，否则为空。 */
    private String overrideType;

    /** 本行对应的策略是否配置了请求控制条件（浏览器白名单或 IP 白名单），来自策略授权
     *  记录联表查询行时有值，来自人工例外行时恒为 {@code null}（app-access-request-control
     *  change design.md Decision 5）。 */
    private Boolean hasRequestControl;
}
