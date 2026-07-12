package cn.nihility.rbac.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import lombok.Getter;

/**
 * 通用分页响应结构，供各模块的分页查询接口复用，避免把 MyBatis-Plus 的
 * {@link IPage}/{@code Page} 等持久层框架类型直接暴露到 controller 签名和 OpenAPI 文档里。
 *
 * @param <T> 分页数据项类型
 */
@Getter
public class PageResult<T> {

    /** 当前页数据列表。 */
    private final List<T> records;

    /** 满足查询条件的总条数。 */
    private final Long total;

    /** 当前页码，从 1 开始。 */
    private final Integer page;

    /** 每页条数。 */
    private final Integer pageSize;

    /**
     * 构造分页响应对象。
     *
     * @param records  当前页数据列表
     * @param total    总条数
     * @param page     当前页码
     * @param pageSize 每页条数
     */
    public PageResult(List<T> records, Long total, Integer page, Integer pageSize) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    /**
     * 基于 MyBatis-Plus 的分页查询结果与转换后的数据列表构造分页响应。
     * 只读取 {@link IPage} 的分页元信息（当前页码、总条数），不将其类型对外暴露。
     *
     * @param records 转换后的当前页数据列表（与 {@code page} 对应，通常由实体列表转换为 VO 得到）
     * @param page    MyBatis-Plus 分页查询结果，提供总条数、页码、每页条数等元信息
     * @param <T>     分页数据项类型
     * @param <E>     MyBatis-Plus 分页查询原始数据类型
     * @return 分页响应对象
     */
    public static <T, E> PageResult<T> of(List<T> records, IPage<E> page) {
        return new PageResult<>(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }
}
