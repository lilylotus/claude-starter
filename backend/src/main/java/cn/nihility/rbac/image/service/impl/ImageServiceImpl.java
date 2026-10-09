package cn.nihility.rbac.image.service.impl;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.image.config.ImageProperties;
import cn.nihility.rbac.image.constant.ImageStatus;
import cn.nihility.rbac.image.dto.ImageBatchDeleteRequest;
import cn.nihility.rbac.image.dto.ImageBatchUploadResultVO;
import cn.nihility.rbac.image.dto.ImagePageQuery;
import cn.nihility.rbac.image.dto.ImageRawContent;
import cn.nihility.rbac.image.dto.ImageVO;
import cn.nihility.rbac.image.entity.ImageEntity;
import cn.nihility.rbac.image.exception.ImageDeletedException;
import cn.nihility.rbac.image.exception.ImageNotFoundException;
import cn.nihility.rbac.image.exception.InvalidImageFileException;
import cn.nihility.rbac.image.mapper.ImageMapper;
import cn.nihility.rbac.image.mapstruct.ImageConvert;
import cn.nihility.rbac.image.service.ImageService;
import cn.nihility.rbac.image.service.support.ImageRecordWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片管理/展示业务逻辑实现（add-image-gallery change tasks.md 6.2）。
 */
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    /** 支持的图片文件后缀白名单（大小写不敏感，比较前统一转小写），design.md Decision 3。 */
    private static final Set<String> ALLOWED_SUFFIXES = Set.of(".jpg", ".jpeg", ".png", ".gif");

    /** 探测不出磁盘文件媒体类型时，按原始后缀名回退的媒体类型映射。 */
    private static final Map<String, String> FALLBACK_CONTENT_TYPES = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".gif", "image/gif");

    /** 图片保存根目录配置。 */
    private final ImageProperties imageProperties;

    /** 图片记录数据访问接口。 */
    private final ImageMapper imageMapper;

    /** 图片记录落库组件，只对数据库插入这一步开启事务，写盘操作在事务之外先完成。 */
    private final ImageRecordWriter imageRecordWriter;

    /** 当前登录操作人用户 id 解析服务，用于填充审计字段。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageVO upload(MultipartFile file) {
        return doUpload(file);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ImageBatchUploadResultVO> batchUpload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("待上传的文件列表不能为空");
        }

        List<ImageBatchUploadResultVO> results = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            String originalFileName = file != null ? file.getOriginalFilename() : null;
            try {
                ImageVO image = doUpload(file);
                results.add(ImageBatchUploadResultVO.builder()
                        .originalFileName(originalFileName)
                        .success(true)
                        .image(image)
                        .build());
            } catch (BusinessException e) {
                // 一个文件的校验/落盘/落库失败不影响其它文件继续处理，收集失败原因而非让
                // 整次批量请求失败（design.md Decision 11，与 Decision 9 批量删除"跳过
                // 无效项、不整体失败"的设计哲学一致）。
                results.add(ImageBatchUploadResultVO.builder()
                        .originalFileName(originalFileName)
                        .success(false)
                        .message(e.getMessage())
                        .build());
            }
        }
        return results;
    }

    /**
     * 单个文件的上传校验/落盘/落库逻辑，供 {@link #upload} 与 {@link #batchUpload} 共用。
     *
     * @param file 待上传的图片文件
     * @return 新增记录的视图对象
     */
    private ImageVO doUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalFileName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFileName)) {
            throw new InvalidImageFileException();
        }
        String suffix = extractSuffix(originalFileName).toLowerCase(Locale.ROOT);
        if (!ALLOWED_SUFFIXES.contains(suffix)) {
            throw new InvalidImageFileException();
        }

        byte[] content = readBytes(file);
        BufferedImage image = decodeImage(content);
        if (image == null) {
            throw new InvalidImageFileException();
        }

        String storedFileName = UUID.randomUUID().toString().replace("-", "") + suffix;
        writeToDisk(content, storedFileName);

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        LocalDateTime now = LocalDateTime.now();
        ImageEntity entity = ImageEntity.builder()
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .fileSize((long) content.length)
                .imageWidth(image.getWidth())
                .imageHeight(image.getHeight())
                .status(ImageStatus.NORMAL)
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();
        imageRecordWriter.insert(entity);

        return ImageConvert.INSTANCE.toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<ImageVO> page(ImagePageQuery query) {
        LambdaQueryWrapper<ImageEntity> wrapper = new LambdaQueryWrapper<ImageEntity>()
                .ne(ImageEntity::getStatus, ImageStatus.DELETED);
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(ImageEntity::getOriginalFileName, query.getKeyword());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(ImageEntity::getCreateTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(ImageEntity::getCreateTime, query.getEndTime());
        }
        return queryPage(query, wrapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<ImageVO> feed(ImagePageQuery query) {
        LambdaQueryWrapper<ImageEntity> wrapper = new LambdaQueryWrapper<ImageEntity>()
                .ne(ImageEntity::getStatus, ImageStatus.DELETED);
        return queryPage(query, wrapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        ImageEntity entity = imageMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), ImageStatus.DELETED)) {
            throw new ImageNotFoundException();
        }

        entity.setStatus(ImageStatus.DELETED);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        imageMapper.updateById(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int batchDelete(ImageBatchDeleteRequest request) {
        List<Long> ids = request.getIds();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        LambdaUpdateWrapper<ImageEntity> wrapper = new LambdaUpdateWrapper<ImageEntity>()
                .in(ImageEntity::getId, ids)
                .eq(ImageEntity::getStatus, ImageStatus.NORMAL)
                .set(ImageEntity::getStatus, ImageStatus.DELETED)
                .set(ImageEntity::getUpdateBy, operator)
                .set(ImageEntity::getUpdateTime, LocalDateTime.now());
        // 单条 UPDATE ... WHERE id IN (...) AND status = NORMAL 原子生效：id 不存在或已是
        // 已删除状态的行天然不满足 WHERE 条件而被跳过，不会报错或影响其余有效 id
        // （design.md Decision 9）。
        return imageMapper.update(null, wrapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImageRawContent loadRawContent(Long id) {
        ImageEntity entity = imageMapper.selectById(id);
        if (entity == null) {
            throw new ImageNotFoundException();
        }
        if (Objects.equals(entity.getStatus(), ImageStatus.DELETED)) {
            throw new ImageDeletedException();
        }

        Path target = resolveRootDir().resolve(entity.getStoredFileName());
        try {
            byte[] content = Files.readAllBytes(target);
            String contentType = Files.probeContentType(target);
            if (!StringUtils.hasText(contentType)) {
                contentType = FALLBACK_CONTENT_TYPES.get(
                        extractSuffix(entity.getOriginalFileName()).toLowerCase(Locale.ROOT));
            }
            return ImageRawContent.builder()
                    .content(content)
                    .contentType(contentType)
                    .build();
        } catch (IOException e) {
            // 记录存在但物理文件缺失/不可读属于数据不一致的系统异常，不定义专属业务异常类型，
            // 走 GlobalExceptionHandler 的兜底 Exception 处理分支（参照 FileUploadServiceImpl）。
            throw new IllegalStateException("图片读取失败：" + entity.getStoredFileName(), e);
        }
    }

    /**
     * 执行分页查询并转换为视图对象，按创建时间降序、id 降序排列（同一时刻创建的记录保持
     * 稳定顺序）。
     *
     * @param query   分页查询参数，仅使用 page/pageSize
     * @param wrapper 已拼好筛选条件的查询条件构造器
     * @return 图片记录的分页结果
     */
    private PageResult<ImageVO> queryPage(ImagePageQuery query, LambdaQueryWrapper<ImageEntity> wrapper) {
        wrapper.orderByDesc(ImageEntity::getCreateTime).orderByDesc(ImageEntity::getId);
        Page<ImageEntity> queryPage = new Page<>(query.getPage(), query.getPageSize());
        Page<ImageEntity> resultPage = imageMapper.selectPage(queryPage, wrapper);
        List<ImageVO> records = ImageConvert.INSTANCE.toVOList(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * 一次性读取上传文件的全部字节内容。
     *
     * @param file 待上传的图片文件
     * @return 文件字节内容
     */
    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败：" + e.getMessage());
        }
    }

    /**
     * 用 {@code ImageIO} 解析字节内容，解析失败（返回 {@code null} 或抛异常）统一视为
     * "无法解析为合法图片"，返回 {@code null} 由调用方拒绝上传（design.md Decision 3，
     * 防御"改后缀名伪装图片"的非图片文件）。
     *
     * @param content 文件字节内容
     * @return 解析得到的 {@link BufferedImage}，解析失败时返回 {@code null}
     */
    private BufferedImage decodeImage(byte[] content) {
        try {
            return ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 把文件字节内容写入磁盘的图片保存根目录下，目录不存在时先创建。
     *
     * @param content        文件字节内容
     * @param storedFileName 磁盘保存文件名
     */
    private void writeToDisk(byte[] content, String storedFileName) {
        Path rootDir = resolveRootDir();
        Path target = rootDir.resolve(storedFileName);
        try {
            Files.createDirectories(rootDir);
            Files.write(target, content);
        } catch (IOException e) {
            throw new BusinessException("文件写入磁盘失败：" + e.getMessage());
        }
    }

    /**
     * 取文件名的后缀（含 {@code .}），不存在则返回空串。
     *
     * @param fileName 文件名
     * @return 后缀，如 {@code .png}；不存在后缀时返回空串
     */
    private String extractSuffix(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex) : "";
    }

    /**
     * 解析图片保存根目录路径，相对路径相对于应用当前工作目录解析。
     *
     * @return 图片保存根目录路径
     */
    private Path resolveRootDir() {
        return Paths.get(imageProperties.getDirectory());
    }
}
