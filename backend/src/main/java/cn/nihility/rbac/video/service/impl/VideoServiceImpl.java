package cn.nihility.rbac.video.service.impl;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.video.config.VideoProperties;
import cn.nihility.rbac.video.constant.VideoStatus;
import cn.nihility.rbac.video.dto.VideoBatchDeleteRequest;
import cn.nihility.rbac.video.dto.VideoPageQuery;
import cn.nihility.rbac.video.dto.VideoPlayTicketVO;
import cn.nihility.rbac.video.dto.VideoStreamResource;
import cn.nihility.rbac.video.dto.VideoVO;
import cn.nihility.rbac.video.entity.VideoEntity;
import cn.nihility.rbac.video.exception.InvalidPlayTicketException;
import cn.nihility.rbac.video.exception.InvalidVideoFileException;
import cn.nihility.rbac.video.exception.VideoFileTooLargeException;
import cn.nihility.rbac.video.exception.VideoNotFoundException;
import cn.nihility.rbac.video.mapper.VideoMapper;
import cn.nihility.rbac.video.mapstruct.VideoConvert;
import cn.nihility.rbac.video.service.VideoService;
import cn.nihility.rbac.video.service.support.VideoPlayTicketService;
import cn.nihility.rbac.video.service.support.VideoRecordWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * 视频管理业务逻辑实现（add-video-management change design.md Decision 2/3）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    /** 后缀 -> 内容类型白名单，仅包含浏览器可原生播放的容器格式（比较前统一转小写）。 */
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".mp4", "video/mp4",
            ".webm", "video/webm",
            ".ogg", "video/ogg",
            ".ogv", "video/ogg");

    /** 魔数校验需要读取的文件头字节数。 */
    private static final int HEADER_LENGTH = 12;

    /** mp4（ISO BMFF）文件头第 4–7 字节的 box 类型标识。 */
    private static final byte[] MP4_FTYP = "ftyp".getBytes(StandardCharsets.US_ASCII);

    /** mp4 中 {@code ftyp} 标识在文件头中的起始偏移。 */
    private static final int MP4_FTYP_OFFSET = 4;

    /** webm（Matroska/EBML）文件头魔数。 */
    private static final byte[] WEBM_EBML = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    /** ogg 文件头魔数。 */
    private static final byte[] OGG_MAGIC = "OggS".getBytes(StandardCharsets.US_ASCII);

    /** 视频模块配置。 */
    private final VideoProperties videoProperties;

    /** 视频记录数据访问接口。 */
    private final VideoMapper videoMapper;

    /** 视频记录落库组件，只对数据库插入开启事务。 */
    private final VideoRecordWriter videoRecordWriter;

    /** 播放凭证读写组件。 */
    private final VideoPlayTicketService videoPlayTicketService;

    /** 当前登录操作人用户 id 解析服务，用于填充审计字段。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public VideoVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        DataSize maxFileSize = videoProperties.getMaxFileSize();
        if (file.getSize() > maxFileSize.toBytes()) {
            throw new VideoFileTooLargeException(formatSize(maxFileSize));
        }

        String originalFileName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFileName)) {
            throw new InvalidVideoFileException();
        }
        String suffix = extractSuffix(originalFileName).toLowerCase(Locale.ROOT);
        String contentType = CONTENT_TYPES.get(suffix);
        if (contentType == null || !matchesMagic(suffix, readHeader(file))) {
            throw new InvalidVideoFileException();
        }

        String storedFileName = UUID.randomUUID().toString().replace("-", "") + suffix;
        Path target = writeToDisk(file, storedFileName);

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        LocalDateTime now = LocalDateTime.now();
        VideoEntity entity = VideoEntity.builder()
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .fileSize(file.getSize())
                .contentType(contentType)
                .status(VideoStatus.NORMAL)
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();
        try {
            videoRecordWriter.insert(entity);
        } catch (RuntimeException e) {
            // 写库失败时清理已落盘的文件，避免留下没有记录指向的孤儿大文件
            deleteQuietly(target);
            throw e;
        }
        return VideoConvert.INSTANCE.toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<VideoVO> page(VideoPageQuery query) {
        LambdaQueryWrapper<VideoEntity> wrapper = new LambdaQueryWrapper<VideoEntity>()
                .ne(VideoEntity::getStatus, VideoStatus.DELETED);
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(VideoEntity::getOriginalFileName, query.getKeyword());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(VideoEntity::getCreateTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(VideoEntity::getCreateTime, query.getEndTime());
        }
        wrapper.orderByDesc(VideoEntity::getCreateTime).orderByDesc(VideoEntity::getId);
        Page<VideoEntity> resultPage = videoMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()),
                wrapper);
        return PageResult.of(VideoConvert.INSTANCE.toVOList(resultPage.getRecords()), resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        VideoEntity entity = requireNormal(id);
        entity.setStatus(VideoStatus.DELETED);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        videoMapper.updateById(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int batchDelete(VideoBatchDeleteRequest request) {
        List<Long> ids = request.getIds();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        // 单条 UPDATE ... WHERE id IN (...) AND status = NORMAL：不存在或已删除的 id 天然不满足
        // 条件而被跳过，不影响其余有效 id
        LambdaUpdateWrapper<VideoEntity> wrapper = new LambdaUpdateWrapper<VideoEntity>()
                .in(VideoEntity::getId, ids)
                .eq(VideoEntity::getStatus, VideoStatus.NORMAL)
                .set(VideoEntity::getStatus, VideoStatus.DELETED)
                .set(VideoEntity::getUpdateBy, operator)
                .set(VideoEntity::getUpdateTime, LocalDateTime.now());
        return videoMapper.update(null, wrapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VideoPlayTicketVO createPlayTicket(Long id) {
        requireNormal(id);
        return VideoPlayTicketVO.builder()
                .ticket(videoPlayTicketService.issue(id))
                .expiresInSeconds(videoPlayTicketService.ttlSeconds())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VideoStreamResource loadStream(Long id, String ticket) {
        if (!videoPlayTicketService.verifyAndRenew(ticket, id)) {
            throw new InvalidPlayTicketException();
        }
        VideoEntity entity = videoMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getStatus(), VideoStatus.NORMAL)) {
            throw new InvalidPlayTicketException();
        }
        Path target = resolveRootDir().resolve(entity.getStoredFileName());
        if (!Files.isReadable(target)) {
            // 记录存在但物理文件缺失属于数据不一致的系统异常，走全局兜底异常处理
            throw new IllegalStateException("视频文件读取失败：" + entity.getStoredFileName());
        }
        return VideoStreamResource.builder()
                .resource(new FileSystemResource(target))
                .contentType(entity.getContentType())
                .build();
    }

    /**
     * 查询状态正常的视频记录，不存在或已删除时抛出 {@link VideoNotFoundException}。
     *
     * @param id 视频记录 id
     * @return 视频记录实体
     */
    private VideoEntity requireNormal(Long id) {
        VideoEntity entity = videoMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getStatus(), VideoStatus.NORMAL)) {
            throw new VideoNotFoundException();
        }
        return entity;
    }

    /**
     * 只读取上传文件的文件头字节用于魔数校验，不整体读入内存。
     *
     * @param file 上传文件
     * @return 文件头字节，文件不足 {@link #HEADER_LENGTH} 字节时返回实际长度
     */
    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEADER_LENGTH);
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败：" + e.getMessage());
        }
    }

    /**
     * 校验文件头魔数是否与后缀对应的容器格式一致，防御"改后缀名伪装视频"的文件。
     *
     * @param suffix 小写后缀（已确认在白名单内）
     * @param header 文件头字节
     * @return 一致时返回 {@code true}
     */
    static boolean matchesMagic(String suffix, byte[] header) {
        return switch (suffix) {
            case ".mp4" -> startsWith(header, MP4_FTYP_OFFSET, MP4_FTYP);
            case ".webm" -> startsWith(header, 0, WEBM_EBML);
            case ".ogg", ".ogv" -> startsWith(header, 0, OGG_MAGIC);
            default -> false;
        };
    }

    /**
     * 判断字节数组从指定偏移开始是否等于给定前缀。
     *
     * @param data   待判断字节数组
     * @param offset 起始偏移
     * @param prefix 期望的字节序列
     * @return 匹配时返回 {@code true}
     */
    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data == null || data.length < offset + prefix.length) {
            return false;
        }
        return Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    /**
     * 把上传文件流式写入视频保存根目录（{@link MultipartFile#transferTo} 不经过 {@code byte[]}），
     * 目录不存在时先创建。
     *
     * @param file           上传文件
     * @param storedFileName 磁盘保存文件名
     * @return 写入后的目标路径
     */
    private Path writeToDisk(MultipartFile file, String storedFileName) {
        Path rootDir = resolveRootDir().toAbsolutePath();
        Path target = rootDir.resolve(storedFileName);
        try {
            Files.createDirectories(rootDir);
            file.transferTo(target);
            return target;
        } catch (IOException e) {
            deleteQuietly(target);
            throw new BusinessException("文件写入磁盘失败：" + e.getMessage());
        }
    }

    /**
     * 静默删除磁盘文件，删除失败只记录告警日志，不影响主流程异常的抛出。
     *
     * @param target 待删除文件路径
     */
    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("清理视频文件失败：{}", target, e);
        }
    }

    /**
     * 把大小格式化为可读文本，整 GB 时显示 GB，否则显示 MB。
     *
     * @param size 大小
     * @return 可读文本，如 {@code 2GB}、{@code 500MB}
     */
    private String formatSize(DataSize size) {
        long gigabytes = size.toGigabytes();
        if (gigabytes > 0 && DataSize.ofGigabytes(gigabytes).equals(size)) {
            return gigabytes + "GB";
        }
        return size.toMegabytes() + "MB";
    }

    /**
     * 取文件名的后缀（含 {@code .}），不存在则返回空串。
     *
     * @param fileName 文件名
     * @return 后缀，如 {@code .mp4}；不存在后缀时返回空串
     */
    private String extractSuffix(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex) : "";
    }

    /**
     * 解析视频保存根目录路径，相对路径相对于应用当前工作目录解析。
     *
     * @return 视频保存根目录路径
     */
    private Path resolveRootDir() {
        return Paths.get(videoProperties.getDirectory());
    }
}
