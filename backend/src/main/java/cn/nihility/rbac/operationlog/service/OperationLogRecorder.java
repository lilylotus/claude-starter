package cn.nihility.rbac.operationlog.service;

import java.util.Map;

/**
 * 操作日志手动记录入口，供组织、用户、任职、应用、角色、权限点、管理员、菜单、
 * 字典类型、字典项共 10 类资源的新增/编辑/启用/停用/删除方法在写库成功后显式调用，
 * 不通过 Spring AOP 切面或注解驱动的方式自动触发。
 *
 * <p>快照为 {@code LinkedHashMap<String, Object>}，key 为中文字段名（如"角色名称"），
 * value 为已经格式化好的人类可读值（如状态字段传"启用"而不是原始码值）；具体记录哪些
 * 字段、如何格式化由各业务模块自行决定，本接口的实现只做 before/after 快照的逐字段
 * diff、写库，不做反射、不猜字段名、不做码值到文案的映射。
 *
 * <p>操作人、操作发起 IP、操作终端类型、操作系统、操作浏览器、原始 User-Agent 均不作为
 * 方法入参，由实现内部自动获取/解析，调用方无需关心。
 */
public interface OperationLogRecorder {

    /**
     * 记录一次新增操作，变更详情中每个字段的旧值为空、新值为快照中对应的值。
     *
     * @param resourceType   资源类型编码，见 {@code OperationLogResourceType}
     * @param targetId       被操作对象主键 id
     * @param targetName     被操作对象名称快照
     * @param afterSnapshot  新增后的字段快照
     */
    void recordCreate(String resourceType, Long targetId, String targetName, Map<String, Object> afterSnapshot);

    /**
     * 记录一次编辑操作，变更详情中仅包含 before/after 两份快照中值不同的字段。
     *
     * @param resourceType    资源类型编码，见 {@code OperationLogResourceType}
     * @param targetId        被操作对象主键 id
     * @param targetName      被操作对象名称快照
     * @param beforeSnapshot  编辑前的字段快照
     * @param afterSnapshot   编辑后的字段快照
     */
    void recordUpdate(String resourceType, Long targetId, String targetName,
            Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot);

    /**
     * 记录一次启用/停用操作。内部复用与 {@link #recordUpdate} 相同的全字段快照 diff
     * 逻辑，仅是语义化的入口——调用方在启停用场景下 before/after 快照除状态字段外
     * 其余字段应保持相同，diff 结果天然只包含状态字段。
     *
     * @param resourceType    资源类型编码，见 {@code OperationLogResourceType}
     * @param targetId        被操作对象主键 id
     * @param targetName      被操作对象名称快照
     * @param enable          {@code true} 表示本次为启用，{@code false} 表示停用
     * @param beforeSnapshot  变更前的字段快照
     * @param afterSnapshot   变更后的字段快照
     */
    void recordStatusChange(String resourceType, Long targetId, String targetName, boolean enable,
            Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot);

    /**
     * 记录一次删除操作，变更详情中每个字段的新值为空、旧值为快照中对应的值。
     *
     * @param resourceType    资源类型编码，见 {@code OperationLogResourceType}
     * @param targetId        被操作对象主键 id
     * @param targetName      被操作对象名称快照
     * @param beforeSnapshot  删除前的字段快照
     */
    void recordDelete(String resourceType, Long targetId, String targetName, Map<String, Object> beforeSnapshot);
}
