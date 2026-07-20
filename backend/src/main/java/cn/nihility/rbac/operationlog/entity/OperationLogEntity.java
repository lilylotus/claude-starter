package cn.nihility.rbac.operationlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 操作日志持久化实体，对应表 {@code tab_operation_log}。日志只追加不更新不删除，
 * {@code updateBy}/{@code updateTime} 写入时与 {@code createBy}/{@code createTime}
 * 一次性赋值为相同的值，之后不再变更。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_operation_log")
public class OperationLogEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务模块中文名，如"组织管理"。 */
    private String module;

    /** 资源类型编码，见 {@code OperationLogResourceType}。 */
    private String resourceType;

    /** 资源类型中文名，如"组织"。 */
    private String resourceName;

    /** 操作类型：1=新增，2=编辑，3=启用，4=停用，5=删除。 */
    private Integer operationType;

    /** 被操作对象主键 id。 */
    private Long targetId;

    /** 被操作对象名称快照。 */
    private String targetName;

    /** 字段变更详情，JSON 数组字符串，每项 {@code {field,oldValue,newValue}}。 */
    private String changeDetail;

    /** 操作发起 IP，取不到时为空。 */
    private String operateIp;

    /** 操作终端类型，从 User-Agent 解析，解析不出时为空。 */
    private String operateTerminal;

    /** 操作系统，从 User-Agent 解析，解析不出时为空。 */
    private String operateOs;

    /** 操作浏览器，从 User-Agent 解析，解析不出时为空。 */
    private String operateBrowser;

    /** 原始 User-Agent 请求头，取不到时为空。 */
    private String operateUserAgent;

    /** 创建人，即操作人。 */
    private String createBy;

    /** 创建时间，即操作发起时间。 */
    private LocalDateTime createTime;

    /** 更新人，恒等于 {@code createBy}。 */
    private String updateBy;

    /** 更新时间，恒等于 {@code createTime}。 */
    private LocalDateTime updateTime;
}
