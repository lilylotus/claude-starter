package cn.nihility.rbac.video.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.video.dto.VideoBatchDeleteRequest;
import cn.nihility.rbac.video.dto.VideoPageQuery;
import cn.nihility.rbac.video.dto.VideoPlayTicketVO;
import cn.nihility.rbac.video.dto.VideoStreamResource;
import cn.nihility.rbac.video.dto.VideoVO;
import cn.nihility.rbac.video.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 视频管理接口（add-video-management change）：上传、分页管理、逻辑删除、播放凭证与流式播放。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "视频管理", description = "视频上传、分页管理、逻辑删除、短期播放凭证与流式播放接口")
public class VideoController {

    /** 视频管理列表默认每页条数。 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 视频管理业务逻辑接口。 */
    private final VideoService videoService;

    /**
     * 上传视频。
     *
     * @param file 待上传的视频文件
     * @return 新增记录的视图对象
     */
    @Operation(summary = "上传视频",
            description = "仅支持 mp4/webm/ogg(ogv) 格式且文件头与格式一致的视频，单个文件不超过 rbac.video.max-file-size"
                    + "（默认 2GB），流式保存到视频保存根目录并新增一条视频记录")
    @PostMapping(value = "/api/videos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoVO upload(
            @Parameter(description = "待上传的视频文件", required = true)
            @RequestParam("file") MultipartFile file) {
        return videoService.upload(file);
    }

    /**
     * 视频管理分页列表，支持按文件名关键字、创建时间区间筛选。
     *
     * @param keyword   按原始文件名模糊匹配的关键字，可选
     * @param startTime 创建时间范围起点（含），可选
     * @param endTime   创建时间范围终点（含），可选
     * @param page      页码，默认第 1 页
     * @param pageSize  每页条数，默认 10 条
     * @return 视频记录的分页结果
     */
    @Operation(summary = "分页查询视频列表",
            description = "按创建时间降序排列，排除已逻辑删除的记录，支持按文件名关键字、创建时间区间筛选")
    @GetMapping("/api/videos")
    public PageResult<VideoVO> page(
            @Parameter(description = "按原始文件名模糊匹配的关键字")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "创建时间范围起点（含），如 2026-07-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "创建时间范围终点（含），如 2026-07-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) Integer pageSize) {
        VideoPageQuery query = new VideoPageQuery();
        query.setKeyword(keyword);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setPage(page);
        query.setPageSize(pageSize);
        return videoService.page(query);
    }

    /**
     * 单条逻辑删除视频记录。
     *
     * @param id 视频记录 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除视频", description = "逻辑删除，仅更新状态，不物理删除磁盘文件；记录不存在或已删除时返回业务错误")
    @DeleteMapping("/api/videos/{id}")
    public Result<Void> delete(
            @Parameter(description = "视频记录 id", required = true)
            @PathVariable Long id) {
        videoService.delete(id);
        return Result.success();
    }

    /**
     * 批量逻辑删除视频记录，无效/已删除的 id 会被跳过。
     *
     * @param request 批量删除请求
     * @return 实际被成功标记为已删除的记录数量
     */
    @Operation(summary = "批量删除视频",
            description = "逻辑删除，对不存在/已删除的 id 跳过而非整体失败，返回实际被成功标记为已删除的数量")
    @DeleteMapping("/api/videos/batch")
    public Result<Integer> batchDelete(@Valid @RequestBody VideoBatchDeleteRequest request) {
        return Result.success(videoService.batchDelete(request));
    }

    /**
     * 为视频换取短期播放凭证。
     *
     * @param id 视频记录 id
     * @return 播放凭证与有效期
     */
    @Operation(summary = "换取视频播放凭证",
            description = "需要登录与 VideoManagement:video:view 权限；返回绑定该视频的随机凭证，前端以凭证拼接流式播放地址"
                    + "作为 <video src>，凭证在持续播放期间滑动续期")
    @PostMapping("/api/videos/{id}/play-ticket")
    public VideoPlayTicketVO playTicket(
            @Parameter(description = "视频记录 id", required = true)
            @PathVariable Long id) {
        return videoService.createPlayTicket(id);
    }

    /**
     * 凭证流式播放视频，支持 HTTP Range（由 Spring MVC 对 {@link Resource} 返回值自动处理，
     * 携带 {@code Range} 请求头时返回 206 分段内容）。
     *
     * @param id     视频记录 id
     * @param ticket 播放凭证
     * @return 视频文件资源
     */
    @Operation(summary = "流式播放视频",
            description = "不要求 identity-token/menu 请求头，只校验播放凭证；支持 Range 分段请求（206），"
                    + "凭证无效、过期、与视频不匹配或视频已删除时返回 HTTP 403")
    @GetMapping("/api/videos/{id}/stream")
    public ResponseEntity<Resource> stream(
            @Parameter(description = "视频记录 id", required = true)
            @PathVariable Long id,
            @Parameter(description = "播放凭证", required = true)
            @RequestParam(required = false) String ticket) {
        VideoStreamResource stream = videoService.loadStream(id, ticket);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stream.getContentType()))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .cacheControl(CacheControl.noStore())
                .body(stream.getResource());
    }
}
