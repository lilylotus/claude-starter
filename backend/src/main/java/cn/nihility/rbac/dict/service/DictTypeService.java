package cn.nihility.rbac.dict.service;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.dict.dto.DictTypeCreateRequest;
import cn.nihility.rbac.dict.dto.DictTypeUpdateRequest;
import cn.nihility.rbac.dict.dto.DictTypeVO;

/**
 * 字典类型业务逻辑接口。
 */
public interface DictTypeService {

    /**
     * 分页查询字典类型（排除已逻辑删除的字典类型），支持按名称或编码模糊搜索。
     *
     * @param keyword  模糊搜索关键字，可为空
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 字典类型的分页结果
     */
    PageResult<DictTypeVO> getPage(String keyword, Integer page, Integer pageSize);

    /**
     * 查询字典类型详情。
     *
     * @param id 字典类型 id
     * @return 字典类型详情
     */
    DictTypeVO getById(Long id);

    /**
     * 创建字典类型。
     *
     * @param request 创建请求
     * @return 创建后的字典类型详情
     */
    DictTypeVO create(DictTypeCreateRequest request);

    /**
     * 更新字典类型基础信息。
     *
     * @param id      字典类型 id
     * @param request 更新请求
     * @return 更新后的字典类型详情
     */
    DictTypeVO update(Long id, DictTypeUpdateRequest request);

    /**
     * 启用字典类型。
     *
     * @param id 字典类型 id
     * @return 更新后的字典类型详情
     */
    DictTypeVO enable(Long id);

    /**
     * 停用字典类型。
     *
     * @param id 字典类型 id
     * @return 更新后的字典类型详情
     */
    DictTypeVO disable(Long id);

    /**
     * 逻辑删除字典类型，删除前需确保不存在未删除的字典项。
     *
     * @param id 字典类型 id
     */
    void delete(Long id);
}
