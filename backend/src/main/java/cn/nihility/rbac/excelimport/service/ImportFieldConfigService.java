package cn.nihility.rbac.excelimport.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigCreateRequest;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigUpdateRequest;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import java.util.List;

/**
 * Excel 导入字段配置业务逻辑接口。
 */
public interface ImportFieldConfigService {

    /**
     * 按业务对象类型分页查询导入字段配置（排除已逻辑删除的配置），按显示序号升序、
     * id 升序排列。
     *
     * @param bizType  业务对象类型，可为空（为空时不按类型过滤）
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 导入字段配置的分页结果
     */
    PageResult<ImportFieldConfigVO> getPage(String bizType, Integer page, Integer pageSize);

    /**
     * 查询导入字段配置详情。
     *
     * @param id 导入字段配置 id
     * @return 导入字段配置详情
     */
    ImportFieldConfigVO getById(Long id);

    /**
     * 创建导入字段配置。若 {@code formFieldDefinitionId} 非空，校验其指向一个存在且
     * 状态为启用、{@code bizType} 与当前配置一致的表单字段定义，{@code fieldCode}
     * 取自该定义的当前值；为空时使用请求中提交的 {@code fieldCode}（必填）。同
     * {@code bizType} 下 {@code fieldCode} 须唯一（未删除范围内）。
     *
     * @param request 创建请求
     * @return 创建后的导入字段配置详情
     */
    ImportFieldConfigVO create(ImportFieldConfigCreateRequest request);

    /**
     * 更新导入字段配置。锁定（系统保护）配置（POSITION 的 {@code __userCode}/
     * {@code __orgCode}）拒绝改绑表单字段定义，也拒绝将 {@code isPrimaryKey}/
     * {@code isRequired} 改为 {@code false}；非锁定配置允许把
     * {@code formFieldDefinitionId} 改绑到同一 {@code bizType} 下另一个状态启用的
     * 表单字段定义，改绑成功时 {@code fieldCode} 同步刷新。
     *
     * @param id      导入字段配置 id
     * @param request 更新请求
     * @return 更新后的导入字段配置详情
     */
    ImportFieldConfigVO update(Long id, ImportFieldConfigUpdateRequest request);

    /**
     * 逻辑删除导入字段配置；锁定（系统保护）配置拒绝删除。
     *
     * @param id 导入字段配置 id
     */
    void delete(Long id);

    /**
     * 查询指定业务对象类型下全部启用状态的导入字段配置，按显示序号升序、id 升序
     * 排列，供导入模板生成与批量导入引擎共用。
     *
     * @param bizType 业务对象类型
     * @return 启用状态的导入字段配置列表
     */
    List<ImportFieldConfigVO> listActiveByBizType(String bizType);
}
