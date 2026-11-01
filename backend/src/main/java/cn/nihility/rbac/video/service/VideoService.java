package cn.nihility.rbac.video.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.video.dto.VideoBatchDeleteRequest;
import cn.nihility.rbac.video.dto.VideoPageQuery;
import cn.nihility.rbac.video.dto.VideoPlayTicketVO;
import cn.nihility.rbac.video.dto.VideoStreamResource;
import cn.nihility.rbac.video.dto.VideoVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 视频管理业务逻辑接口（add-video-management change）。
 */
public interface VideoService {

    /**
     * 上传视频：校验后缀属于 mp4/webm/ogg/ogv 白名单、文件头魔数与后缀格式一致、大小不超过
     * {@code rbac.video.max-file-size}，流式写入视频保存根目录（不整体读入内存），再新增一条
     * 视频记录；写库失败时删除已写入的磁盘文件。
     *
     * @param file 待上传的视频文件
     * @return 新增记录的视图对象
     */
    VideoVO upload(MultipartFile file);

    /**
     * 分页列表：按创建时间降序，排除已逻辑删除的记录，支持文件名关键字、创建时间区间筛选。
     *
     * @param query 分页查询参数
     * @return 视频记录的分页结果
     */
    PageResult<VideoVO> page(VideoPageQuery query);

    /**
     * 单条逻辑删除：仅更新状态为已删除，不物理删除磁盘文件；记录不存在或已删除时抛出
     * {@code VideoNotFoundException}。
     *
     * @param id 视频记录 id
     */
    void delete(Long id);

    /**
     * 批量逻辑删除：跳过不存在或已删除的 id，不抛出异常。
     *
     * @param request 批量删除请求
     * @return 实际被成功标记为已删除的记录数量
     */
    int batchDelete(VideoBatchDeleteRequest request);

    /**
     * 为状态正常的视频生成短期播放凭证；视频不存在或已删除时抛出 {@code VideoNotFoundException}。
     *
     * @param id 视频记录 id
     * @return 播放凭证与有效期
     */
    VideoPlayTicketVO createPlayTicket(Long id);

    /**
     * 校验播放凭证并返回视频文件资源，校验通过时滑动续期凭证；凭证缺失/无效/过期/与视频
     * 不匹配，或视频不存在/已删除时抛出 {@code InvalidPlayTicketException}；记录存在但磁盘文件
     * 缺失时抛出 {@link IllegalStateException}（数据不一致的系统异常）。
     *
     * @param id     视频记录 id
     * @param ticket 播放凭证
     * @return 视频文件资源与内容类型
     */
    VideoStreamResource loadStream(Long id, String ticket);
}
