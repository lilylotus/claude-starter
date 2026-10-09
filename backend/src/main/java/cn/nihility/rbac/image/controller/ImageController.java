package cn.nihility.rbac.image.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.image.dto.ImageBatchDeleteRequest;
import cn.nihility.rbac.image.dto.ImageBatchUploadResultVO;
import cn.nihility.rbac.image.dto.ImagePageQuery;
import cn.nihility.rbac.image.dto.ImageRawContent;
import cn.nihility.rbac.image.dto.ImageVO;
import cn.nihility.rbac.image.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片管理/展示接口（add-image-gallery change tasks.md 7.1）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "图片管理与展示", description = "图片上传、分页管理、逻辑删除、展示 feed、原图内容访问接口")
public class ImageController {

    /** 探测不出图片媒体类型时的兜底类型。 */
    private static final MediaType FALLBACK_MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;

    /** 图片管理页默认每页条数（design.md Decision 6）。 */
    private static final int DEFAULT_MANAGEMENT_PAGE_SIZE = 10;

    /** 图片展示页 feed 默认每页条数（design.md Decision 6）。 */
    private static final int DEFAULT_FEED_PAGE_SIZE = 20;

    /** 图片管理/展示业务逻辑接口。 */
    private final ImageService imageService;

    /**
     * 上传图片。
     *
     * @param file 待上传的图片文件
     * @return 新增记录的视图对象
     */
    @Operation(summary = "上传图片",
            description = "仅支持 jpg/jpeg/png/gif 格式且内容可解析的图片文件，保存到磁盘的图片保存根目录下并新增一条图片记录")
    @PostMapping(value = "/api/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageVO upload(
            @Parameter(description = "待上传的图片文件", required = true)
            @RequestParam("file") MultipartFile file) {
        return imageService.upload(file);
    }

    /**
     * 批量上传图片，一个文件的失败不影响其它文件继续处理。
     *
     * @param files 待上传的图片文件列表
     * @return 每个文件各自的处理结果，顺序与传入的文件顺序一致
     */
    @Operation(summary = "批量上传图片",
            description = "一次请求接收多个文件，每个文件独立校验/落盘/落库，某个文件不合法不影响其它文件，"
                    + "返回每个文件各自的成功/失败结果")
    @PostMapping(value = "/api/images/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<ImageBatchUploadResultVO> batchUpload(
            @Parameter(description = "待上传的图片文件列表", required = true)
            @RequestParam("files") List<MultipartFile> files) {
        return imageService.batchUpload(files);
    }

    /**
     * 图片管理页分页列表，支持按文件名关键字、创建时间区间筛选。
     *
     * @param keyword   按原始文件名模糊匹配的关键字，可选
     * @param startTime 创建时间范围起点（含），可选
     * @param endTime   创建时间范围终点（含），可选
     * @param page      页码，默认第 1 页
     * @param pageSize  每页条数，默认 10 条
     * @return 图片记录的分页结果
     */
    @Operation(summary = "分页查询图片管理列表",
            description = "按创建时间降序排列，排除已逻辑删除的记录，支持按文件名关键字、创建时间区间筛选，两者均可选、可组合")
    @GetMapping("/api/images")
    public PageResult<ImageVO> page(
            @Parameter(description = "按原始文件名模糊匹配的关键字")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "创建时间范围起点（含），如 2026-07-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "创建时间范围终点（含），如 2026-07-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_MANAGEMENT_PAGE_SIZE) Integer pageSize) {
        ImagePageQuery query = new ImagePageQuery();
        query.setKeyword(keyword);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setPage(page);
        query.setPageSize(pageSize);
        return imageService.page(query);
    }

    /**
     * 图片展示页分页 feed，不提供筛选参数。
     *
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 20 条
     * @return 图片记录的分页结果，每条记录含图片宽高供前端预先计算瀑布流布局
     */
    @Operation(summary = "分页查询图片展示 feed",
            description = "供无限滚动场景使用，按创建时间降序排列，排除已逻辑删除的记录，每条记录含图片宽高，不提供筛选参数")
    @GetMapping("/api/images/feed")
    public PageResult<ImageVO> feed(
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 20 条")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_FEED_PAGE_SIZE) Integer pageSize) {
        ImagePageQuery query = new ImagePageQuery();
        query.setPage(page);
        query.setPageSize(pageSize);
        return imageService.feed(query);
    }

    /**
     * 单条逻辑删除图片记录。
     *
     * @param id 图片记录 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除图片", description = "逻辑删除，仅更新状态，不物理删除磁盘文件；记录不存在或已删除时返回业务错误")
    @DeleteMapping("/api/images/{id}")
    public Result<Void> delete(
            @Parameter(description = "图片记录 id", required = true)
            @PathVariable Long id) {
        imageService.delete(id);
        return Result.success();
    }

    /**
     * 批量逻辑删除图片记录，无效/已删除的 id 会被跳过而不影响其余有效 id。
     *
     * @param request 批量删除请求
     * @return 实际被成功标记为已删除的记录数量
     */
    @Operation(summary = "批量删除图片",
            description = "逻辑删除，对不存在/已删除的 id 跳过而非整体失败，返回实际被成功标记为已删除的数量")
    @DeleteMapping("/api/images/batch")
    public Result<Integer> batchDelete(@Valid @RequestBody ImageBatchDeleteRequest request) {
        return Result.success(imageService.batchDelete(request));
    }

    /**
     * 按图片记录 id 获取原图二进制内容，供 {@code <img>} 标签直接引用展示，不设置
     * {@code Content-Disposition: attachment}。
     *
     * @param id 图片记录 id
     * @return 图片二进制内容
     */
    @Operation(summary = "获取原图内容",
            description = "返回图片原始字节内容，非下载语义（不含 Content-Disposition: attachment），"
                    + "图片管理网格缩略图、图片展示瀑布流、点击查看原图三处场景复用同一个接口；仅状态为正常的记录允许访问")
    @GetMapping("/api/images/{id}/raw")
    public ResponseEntity<byte[]> raw(
            @Parameter(description = "图片记录 id", required = true)
            @PathVariable Long id) {
        ImageRawContent result = imageService.loadRawContent(id);
        MediaType mediaType = StringUtils.hasText(result.getContentType())
                ? MediaType.parseMediaType(result.getContentType()) : FALLBACK_MEDIA_TYPE;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(result.getContent());
    }
}
