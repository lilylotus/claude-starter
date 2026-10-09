package cn.nihility.rbac.image.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import cn.nihility.rbac.image.service.support.ImageRecordWriter;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * {@link ImageServiceImpl} 的单元测试，重点覆盖：上传格式/内容非法拒绝、宽高解析正确、
 * 单条/批量逻辑删除（含批量删除混入无效 id 不报错的场景）、关键字/时间范围筛选、删除后
 * 原图内容不可访问（add-image-gallery change specs/image-management/spec.md、
 * specs/image-gallery/spec.md）。
 */
@ExtendWith(MockitoExtension.class)
class ImageServiceImplTest {

    /** 落盘目录，测试用临时目录，避免污染仓库工作目录。 */
    @TempDir
    private Path tempDir;

    /**
     * 初始化 MyBatis-Plus Lambda 列缓存，供断言 {@code LambdaQueryWrapper}/
     * {@code LambdaUpdateWrapper} 生成的 SQL 片段的用例使用；必须显式开启
     * {@code mapUnderscoreToCamelCase}（与本项目 {@code mybatis/mybatis.conf} 里真实
     * MyBatis 配置一致），否则默认关闭该项的 {@link Configuration} 生成的 {@code TableInfo}
     * 会把列名错误地缓存成驼峰字段名本身（照抄 {@code ApprovalRequestServiceImplTest} 的做法）。
     */
    @BeforeAll
    static void primeLambdaColumnCache() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "imageServiceTest");
        assistant.setCurrentNamespace(ImageEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, ImageEntity.class);
    }

    /** 被测服务的图片记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private ImageMapper imageMapper;

    /** 被测服务的图片记录落库依赖，使用 Mockito 打桩。 */
    @Mock
    private ImageRecordWriter imageRecordWriter;

    /** 被测服务的当前登录操作人用户 id 解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务实例。 */
    private ImageServiceImpl imageService;

    /**
     * 每个用例执行前重新构造被测服务，落盘根目录指向 JUnit 临时目录。
     */
    @BeforeEach
    void setUp() {
        ImageProperties properties = new ImageProperties();
        properties.setDirectory(tempDir.toString());
        imageService = new ImageServiceImpl(properties, imageMapper, imageRecordWriter, currentOperatorService);
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
    }

    /**
     * 上传合法 PNG 图片时，应解析出与实际尺寸一致的宽高，落库记录状态为"正常"，磁盘保存
     * 文件名与原始文件名不同。
     */
    @Test
    void upload_shouldInsertNormalRecordWithParsedDimensions_whenValidPngProvided() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes(32, 20));

        ImageVO vo = imageService.upload(file);

        ArgumentCaptor<ImageEntity> captor = ArgumentCaptor.forClass(ImageEntity.class);
        verify(imageRecordWriter).insert(captor.capture());
        ImageEntity entity = captor.getValue();

        assertThat(entity.getOriginalFileName()).isEqualTo("photo.png");
        assertThat(entity.getStoredFileName()).isNotEqualTo("photo.png").endsWith(".png");
        assertThat(entity.getImageWidth()).isEqualTo(32);
        assertThat(entity.getImageHeight()).isEqualTo(20);
        assertThat(entity.getStatus()).isEqualTo(ImageStatus.NORMAL);
        assertThat(entity.getCreateBy()).isEqualTo("1");
        assertThat(vo.getImageWidth()).isEqualTo(32);
        assertThat(vo.getImageHeight()).isEqualTo(20);

        Path savedFile = tempDir.resolve(entity.getStoredFileName());
        assertThat(Files.exists(savedFile)).isTrue();
    }

    /**
     * 后缀不在白名单内（如 .webp）时应拒绝上传，不落盘也不落库。
     */
    @Test
    void upload_shouldThrowInvalidImageFileException_whenSuffixNotSupported() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.webp", "image/webp", pngBytes(10, 10));

        assertThatThrownBy(() -> imageService.upload(file)).isInstanceOf(InvalidImageFileException.class);
        verify(imageRecordWriter, never()).insert(any());
    }

    /**
     * 后缀受支持但文件内容并非合法图片数据（把文本文件改了后缀名）时应拒绝上传。
     */
    @Test
    void upload_shouldThrowInvalidImageFileException_whenContentNotDecodable() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png",
                "not-a-real-image".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> imageService.upload(file)).isInstanceOf(InvalidImageFileException.class);
        verify(imageRecordWriter, never()).insert(any());
    }

    /**
     * 上传空文件时应拒绝，不产生磁盘文件或数据库落库调用。
     */
    @Test
    void upload_shouldThrowBusinessException_whenFileEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> imageService.upload(emptyFile)).isInstanceOf(BusinessException.class);
        verify(imageRecordWriter, never()).insert(any());
    }

    /**
     * 批量上传全部合法图片时，每个文件都应成功落盘落库，返回结果列表按传入顺序逐一标记
     * 成功并携带对应的新增记录。
     */
    @Test
    void batchUpload_shouldMarkAllSuccess_whenAllFilesValid() throws IOException {
        MockMultipartFile first = new MockMultipartFile("files", "a.png", "image/png", pngBytes(10, 10));
        MockMultipartFile second = new MockMultipartFile("files", "b.png", "image/png", pngBytes(20, 20));

        List<ImageBatchUploadResultVO> results = imageService.batchUpload(List.of(first, second));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(0).getOriginalFileName()).isEqualTo("a.png");
        assertThat(results.get(0).getImage()).isNotNull();
        assertThat(results.get(1).isSuccess()).isTrue();
        assertThat(results.get(1).getOriginalFileName()).isEqualTo("b.png");
        verify(imageRecordWriter, times(2)).insert(any());
    }

    /**
     * 批量上传中混入不合法文件（后缀不受支持）时，合法文件仍应成功落盘落库，不合法文件
     * 被跳过并在结果列表中标记失败、携带失败原因，不影响其它文件的处理结果。
     */
    @Test
    void batchUpload_shouldSkipInvalidFile_andKeepOthersSucceeding() throws IOException {
        MockMultipartFile valid = new MockMultipartFile("files", "a.png", "image/png", pngBytes(10, 10));
        MockMultipartFile invalid = new MockMultipartFile("files", "b.webp", "image/webp", pngBytes(10, 10));

        List<ImageBatchUploadResultVO> results = imageService.batchUpload(List.of(valid, invalid));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(1).isSuccess()).isFalse();
        assertThat(results.get(1).getOriginalFileName()).isEqualTo("b.webp");
        assertThat(results.get(1).getMessage()).isNotBlank();
        assertThat(results.get(1).getImage()).isNull();
        verify(imageRecordWriter, times(1)).insert(any());
    }

    /**
     * 批量上传传入空文件列表时应拒绝请求，不处理任何文件。
     */
    @Test
    void batchUpload_shouldThrowBusinessException_whenFilesEmpty() {
        assertThatThrownBy(() -> imageService.batchUpload(List.of())).isInstanceOf(BusinessException.class);
        verify(imageRecordWriter, never()).insert(any());
    }

    /**
     * 删除状态为"正常"的图片记录时，应更新为已删除状态，不做任何磁盘文件操作。
     */
    @Test
    void delete_shouldMarkDeleted_whenRecordNormal() {
        ImageEntity entity = ImageEntity.builder().id(1L).status(ImageStatus.NORMAL).build();
        when(imageMapper.selectById(1L)).thenReturn(entity);

        imageService.delete(1L);

        assertThat(entity.getStatus()).isEqualTo(ImageStatus.DELETED);
        verify(imageMapper).updateById(entity);
    }

    /**
     * 删除不存在的图片记录 id 时应抛出 {@link ImageNotFoundException}。
     */
    @Test
    void delete_shouldThrowImageNotFoundException_whenRecordMissing() {
        when(imageMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> imageService.delete(999L)).isInstanceOf(ImageNotFoundException.class);
    }

    /**
     * 删除已被逻辑删除的图片记录时应同样抛出 {@link ImageNotFoundException}，不重复生效。
     */
    @Test
    void delete_shouldThrowImageNotFoundException_whenAlreadyDeleted() {
        ImageEntity entity = ImageEntity.builder().id(2L).status(ImageStatus.DELETED).build();
        when(imageMapper.selectById(2L)).thenReturn(entity);

        assertThatThrownBy(() -> imageService.delete(2L)).isInstanceOf(ImageNotFoundException.class);
        verify(imageMapper, never()).updateById(any(ImageEntity.class));
    }

    /**
     * 批量删除：构造的更新条件应同时按 id 集合与"正常"状态过滤，使不存在/已删除的 id 在
     * SQL 层面天然被跳过；返回值直接取 mapper 实际更新的行数（仅计入有效 id）。
     */
    @Test
    void batchDelete_shouldFilterByIdsAndNormalStatus_andReturnMapperAffectedRows() {
        ImageBatchDeleteRequest request = new ImageBatchDeleteRequest();
        request.setIds(List.of(1L, 2L, 999L));
        when(imageMapper.update(eq(null), any())).thenReturn(2);

        int updated = imageService.batchDelete(request);

        assertThat(updated).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ImageEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(imageMapper).update(eq(null), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).containsIgnoringCase("status").containsIgnoringCase("IN");
    }

    /**
     * 批量删除传入空 id 列表时直接返回 0，不调用 mapper。
     */
    @Test
    void batchDelete_shouldReturnZero_whenIdsEmpty() {
        ImageBatchDeleteRequest request = new ImageBatchDeleteRequest();
        request.setIds(List.of());

        int updated = imageService.batchDelete(request);

        assertThat(updated).isZero();
        verify(imageMapper, never()).update(any(), any());
    }

    /**
     * 图片管理页分页列表指定关键字与时间范围时，查询条件应包含对应的 LIKE / 时间比较条件。
     */
    @Test
    void page_shouldApplyKeywordAndTimeRangeFilters() {
        ImagePageQuery query = new ImagePageQuery();
        query.setPage(1);
        query.setPageSize(10);
        query.setKeyword("风景");
        query.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 1, 31, 23, 59));
        when(imageMapper.selectPage(any(), any())).thenReturn(emptyPage(1, 10));

        imageService.page(query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ImageEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(imageMapper).selectPage(any(), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).containsIgnoringCase("LIKE");
        assertThat(sqlSegment).contains(">=");
        assertThat(sqlSegment).contains("<=");
    }

    /**
     * 图片展示 feed 接口不使用筛选字段，查询条件中不应出现关键字 LIKE 条件。
     */
    @Test
    void feed_shouldNotApplyKeywordFilter() {
        ImagePageQuery query = new ImagePageQuery();
        query.setPage(1);
        query.setPageSize(20);
        when(imageMapper.selectPage(any(), any())).thenReturn(emptyPage(1, 20));

        imageService.feed(query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ImageEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(imageMapper).selectPage(any(), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).doesNotContainIgnoringCase("LIKE");
    }

    /**
     * 读取状态为"正常"的图片原图内容时，应返回磁盘文件的真实字节内容。
     */
    @Test
    void loadRawContent_shouldReturnContent_whenRecordNormal() throws IOException {
        String storedFileName = "abc123.png";
        byte[] content = pngBytes(5, 5);
        Files.write(tempDir.resolve(storedFileName), content);

        ImageEntity entity = ImageEntity.builder()
                .id(1L)
                .originalFileName("原始图片.png")
                .storedFileName(storedFileName)
                .status(ImageStatus.NORMAL)
                .build();
        when(imageMapper.selectById(1L)).thenReturn(entity);

        ImageRawContent result = imageService.loadRawContent(1L);

        assertThat(result.getContent()).isEqualTo(content);
    }

    /**
     * 读取不存在的图片记录 id 时应抛出 {@link ImageNotFoundException}。
     */
    @Test
    void loadRawContent_shouldThrowImageNotFoundException_whenRecordMissing() {
        when(imageMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> imageService.loadRawContent(999L)).isInstanceOf(ImageNotFoundException.class);
    }

    /**
     * 读取已被逻辑删除的图片记录内容时应拒绝并抛出 {@link ImageDeletedException}。
     */
    @Test
    void loadRawContent_shouldThrowImageDeletedException_whenRecordDeleted() {
        ImageEntity entity = ImageEntity.builder().id(3L).status(ImageStatus.DELETED).build();
        when(imageMapper.selectById(3L)).thenReturn(entity);

        assertThatThrownBy(() -> imageService.loadRawContent(3L)).isInstanceOf(ImageDeletedException.class);
    }

    /**
     * 生成一张指定宽高的内存 PNG 图片字节内容，供上传/原图读取相关用例复用。
     *
     * @param width  宽度（像素）
     * @param height 高度（像素）
     * @return PNG 格式的字节内容
     */
    private byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    /**
     * 构造一个空的 MyBatis-Plus 分页结果，供只关心查询条件构造、不关心返回数据的用例使用。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 空的分页结果
     */
    private Page<ImageEntity> emptyPage(int page, int pageSize) {
        return new Page<>(page, pageSize);
    }
}
