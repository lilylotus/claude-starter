package cn.nihility.rbac.dict.service;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.dict.dto.DictItemCreateRequest;
import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.dto.DictItemUpdateRequest;
import cn.nihility.rbac.dict.dto.DictItemVO;
import java.util.List;

/**
 * 字典项业务逻辑接口。
 */
public interface DictItemService {

    /**
     * 按所属字典类型分页查询字典项（排除已逻辑删除的字典项）。
     *
     * @param dictTypeId 所属字典类型 id
     * @param page       页码，从 1 开始
     * @param pageSize   每页条数
     * @return 字典项的分页结果
     */
    PageResult<DictItemVO> getPage(Long dictTypeId, Integer page, Integer pageSize);

    /**
     * 查询字典项详情（回填所属字典类型名称）。
     *
     * @param id 字典项 id
     * @return 字典项详情
     */
    DictItemVO getById(Long id);

    /**
     * 创建字典项。
     *
     * @param request 创建请求
     * @return 创建后的字典项详情
     */
    DictItemVO create(DictItemCreateRequest request);

    /**
     * 更新字典项基础信息，不支持修改所属字典类型。
     *
     * @param id      字典项 id
     * @param request 更新请求
     * @return 更新后的字典项详情
     */
    DictItemVO update(Long id, DictItemUpdateRequest request);

    /**
     * 启用字典项。
     *
     * @param id 字典项 id
     * @return 更新后的字典项详情
     */
    DictItemVO enable(Long id);

    /**
     * 停用字典项。
     *
     * @param id 字典项 id
     * @return 更新后的字典项详情
     */
    DictItemVO disable(Long id);

    /**
     * 逻辑删除字典项。
     *
     * @param id 字典项 id
     */
    void delete(Long id);

    /**
     * 按字典类型编码查询其下全部启用状态的字典项精简列表，供业务模块下拉框场景使用。
     * 若 {@code typeCode} 不存在、对应字典类型已被逻辑删除或已被停用，返回空列表而非报错。
     *
     * @param typeCode 字典类型编码
     * @return 启用状态的字典项精简列表，按显示序号降序、id 升序排列
     */
    List<DictItemOptionVO> getEnabledOptions(String typeCode);
}
