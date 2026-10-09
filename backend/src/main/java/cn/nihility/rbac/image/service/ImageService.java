package cn.nihility.rbac.image.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.image.dto.ImageBatchDeleteRequest;
import cn.nihility.rbac.image.dto.ImageBatchUploadResultVO;
import cn.nihility.rbac.image.dto.ImagePageQuery;
import cn.nihility.rbac.image.dto.ImageRawContent;
import cn.nihility.rbac.image.dto.ImageVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片管理/展示业务逻辑接口（add-image-gallery change tasks.md 6.1）。
 */
public interface ImageService {

    /**
     * 上传图片：校验原始文件名后缀属于 jpg/jpeg/png/gif 白名单且内容能被
     * {@code ImageIO} 解析出宽高，保存到磁盘的图片保存根目录下，保存文件名使用随机字符串
     * 加原始文件后缀生成，并新增一条图片记录。
     *
     * @param file 待上传的图片文件
     * @return 新增记录的视图对象
     */
    ImageVO upload(MultipartFile file);

    /**
     * 批量上传图片：对每个文件独立应用与 {@link #upload} 相同的校验/落盘/落库逻辑，一个
     * 文件失败（格式不在白名单、内容无法解析为图片等）不影响其它文件继续处理，返回每个
     * 文件各自的成功/失败结果，顺序与传入的文件顺序一致（design.md Decision 11）。
     *
     * @param files 待上传的图片文件列表，不能为空
     * @return 每个文件各自的处理结果
     */
    List<ImageBatchUploadResultVO> batchUpload(List<MultipartFile> files);

    /**
     * 图片管理页分页列表：按创建时间降序排列，排除已逻辑删除的记录，支持按文件名关键字、
     * 创建时间区间筛选（design.md Decision 10）。
     *
     * @param query 分页查询参数（含可选筛选条件）
     * @return 图片记录的分页结果
     */
    PageResult<ImageVO> page(ImagePageQuery query);

    /**
     * 图片展示页分页 feed：按创建时间降序排列，排除已逻辑删除的记录，不使用筛选条件，与
     * 管理页复用同一张表、同一个排序规则（design.md Decision 6）。
     *
     * @param query 分页查询参数（仅使用 page/pageSize）
     * @return 图片记录的分页结果
     */
    PageResult<ImageVO> feed(ImagePageQuery query);

    /**
     * 单条逻辑删除：仅更新记录状态为已删除，不物理删除磁盘文件；记录不存在或已删除时抛出
     * {@code ImageNotFoundException}。
     *
     * @param id 图片记录 id
     */
    void delete(Long id);

    /**
     * 批量逻辑删除：对每个 id，仅当记录存在且未被逻辑删除时才更新其状态为已删除；id 不存在
     * 或已被逻辑删除的情况会被跳过，不抛出异常（design.md Decision 9）。
     *
     * @param request 批量删除请求
     * @return 实际被成功标记为已删除的记录数量
     */
    int batchDelete(ImageBatchDeleteRequest request);

    /**
     * 读取原图二进制内容：记录不存在时抛出 {@code ImageNotFoundException}，记录已被逻辑
     * 删除时抛出 {@code ImageDeletedException}。
     *
     * @param id 图片记录 id
     * @return 原图内容读取结果
     */
    ImageRawContent loadRawContent(Long id);
}
