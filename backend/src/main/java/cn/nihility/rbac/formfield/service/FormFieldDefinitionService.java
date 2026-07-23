package cn.nihility.rbac.formfield.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionCreateRequest;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionUpdateRequest;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.dto.FormFieldRenderItemVO;
import java.util.List;

/**
 * 表单字段定义业务逻辑接口。
 */
public interface FormFieldDefinitionService {

    /**
     * 按业务对象类型分页查询字段定义（排除已逻辑删除的定义）。
     *
     * @param bizType  业务对象类型，可为空（为空时不按类型过滤）
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 字段定义的分页结果
     */
    PageResult<FormFieldDefinitionVO> getPage(String bizType, Integer page, Integer pageSize);

    /**
     * 查询字段定义详情。
     *
     * @param id 字段定义 id
     * @return 字段定义详情
     */
    FormFieldDefinitionVO getById(Long id);

    /**
     * 创建字段定义，校验绑定的元数据字段可用性、字典下拉/多选字典下拉的
     * dictTypeId 必填性；fieldCode 取自所绑定元数据字段的当前值，不由请求提交。
     *
     * @param request 创建请求
     * @return 创建后的字段定义详情
     */
    FormFieldDefinitionVO create(FormFieldDefinitionCreateRequest request);

    /**
     * 更新字段定义，bizType 创建后不可修改。对绑定承重字段（locked=true）的定义，
     * 拒绝将 isRequired/showInCreate/showInEdit 改为 false，也拒绝改绑
     * metadataFieldId；非锁定（locked=false）定义允许把 metadataFieldId 改绑到同一
     * bizType 下另一个状态启用且未被其他有效定义占用的元数据字段，请求体
     * metadataFieldId 省略或与当前值相同时视为不改绑；改绑成功时 fieldCode 同步
     * 刷新为新绑定元数据字段的当前值。
     *
     * @param id      字段定义 id
     * @param request 更新请求
     * @return 更新后的字段定义详情
     */
    FormFieldDefinitionVO update(Long id, FormFieldDefinitionUpdateRequest request);

    /**
     * 启用字段定义。
     *
     * @param id 字段定义 id
     * @return 更新后的字段定义详情
     */
    FormFieldDefinitionVO enable(Long id);

    /**
     * 停用字段定义；绑定承重字段的定义拒绝停用。
     *
     * @param id 字段定义 id
     * @return 更新后的字段定义详情
     */
    FormFieldDefinitionVO disable(Long id);

    /**
     * 逻辑删除字段定义；绑定承重字段的定义拒绝删除，其余释放其绑定的元数据字段。
     *
     * @param id 字段定义 id
     */
    void delete(Long id);

    /**
     * 查询指定业务对象类型下全部启用状态、且非承重字段（locked=false）的定义，
     * 供组织/用户/任职/应用四个业务模块的新增/编辑校验管线读取。
     *
     * @param bizType 业务对象类型
     * @return 非承重字段的启用状态定义列表
     */
    List<FormFieldDefinitionVO> listActiveByBizType(String bizType);

    /**
     * 构造指定业务对象类型的动态字段渲染元数据：全部启用状态的定义（含承重字段），
     * 按显示序号降序排列；控件类型为字典下拉或多选字典下拉的定义内嵌其关联字典类型
     * 下的启用字典项。
     *
     * @param bizType 业务对象类型
     * @return 渲染元数据列表
     */
    List<FormFieldRenderItemVO> buildRenderSchema(String bizType);
}
