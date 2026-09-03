package cn.nihility.rbac.workflow.dto;

/**
 * 待办/已办任务查询条件。
 *
 * @param businessType 业务对象类型过滤，可为空表示不过滤
 * @param page         页码，从 1 开始
 * @param pageSize     每页大小
 */
public record TaskQuery(String businessType, Integer page, Integer pageSize) {

    /**
     * 归一化页码，未提供或非法值时回退为 1。
     *
     * @return 有效页码
     */
    public int effectivePage() {
        return page != null && page > 0 ? page : 1;
    }

    /**
     * 归一化每页大小，未提供或非法值时回退为 10。
     *
     * @return 有效每页大小
     */
    public int effectivePageSize() {
        return pageSize != null && pageSize > 0 ? pageSize : 10;
    }
}
