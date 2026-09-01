package cn.nihility.rbac.chat.service;

import cn.nihility.rbac.chat.dto.SensitiveWordCreateRequest;
import cn.nihility.rbac.chat.dto.SensitiveWordVO;
import cn.nihility.rbac.common.result.PageResult;

/**
 * 敏感词后台管理业务逻辑接口：分页查询、新增、删除（物理删除）、启用/停用词条，
 * 任一写操作完成后均触发 {@link SensitiveWordFilterService#reload()} 使变更立即生效
 * （chat-security spec"敏感词库后台管理"需求）。
 */
public interface SensitiveWordService {

    /**
     * 分页查询敏感词。
     *
     * @param keyword  词条关键字模糊匹配，可为空
     * @param status   状态精确过滤，可为空
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 敏感词的分页结果
     */
    PageResult<SensitiveWordVO> getPage(String keyword, Integer status, Integer page, Integer pageSize);

    /**
     * 新增敏感词。
     *
     * @param request 创建请求
     * @return 创建后的敏感词详情
     */
    SensitiveWordVO create(SensitiveWordCreateRequest request);

    /**
     * 物理删除敏感词。
     *
     * @param id 敏感词 id
     */
    void delete(Long id);

    /**
     * 启用敏感词。
     *
     * @param id 敏感词 id
     * @return 更新后的敏感词详情
     */
    SensitiveWordVO enable(Long id);

    /**
     * 停用敏感词。
     *
     * @param id 敏感词 id
     * @return 更新后的敏感词详情
     */
    SensitiveWordVO disable(Long id);
}
