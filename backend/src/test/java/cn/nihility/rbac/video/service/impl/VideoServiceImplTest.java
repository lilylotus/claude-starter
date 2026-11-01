package cn.nihility.rbac.video.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.video.config.VideoProperties;
import cn.nihility.rbac.video.constant.VideoStatus;
import cn.nihility.rbac.video.dto.VideoBatchDeleteRequest;
import cn.nihility.rbac.video.dto.VideoPlayTicketVO;
import cn.nihility.rbac.video.dto.VideoStreamResource;
import cn.nihility.rbac.video.dto.VideoVO;
import cn.nihility.rbac.video.entity.VideoEntity;
import cn.nihility.rbac.video.exception.InvalidPlayTicketException;
import cn.nihility.rbac.video.exception.InvalidVideoFileException;
import cn.nihility.rbac.video.exception.VideoFileTooLargeException;
import cn.nihility.rbac.video.exception.VideoNotFoundException;
import cn.nihility.rbac.video.mapper.VideoMapper;
import cn.nihility.rbac.video.service.support.VideoPlayTicketService;
import cn.nihility.rbac.video.service.support.VideoRecordWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/**
 * {@link VideoServiceImpl} 的单元测试，覆盖：格式白名单与魔数校验、大小上限、流式落盘与写库失败
 * 清理、逻辑删除、播放凭证签发与流式播放校验（add-video-management change
 * specs/video-management/spec.md）。
 */
@ExtendWith(MockitoExtension.class)
class VideoServiceImplTest {

    /** 落盘目录，测试用临时目录，避免污染仓库工作目录。 */
    @TempDir
    private Path tempDir;

    /** 视频记录数据访问依赖。 */
    @Mock
    private VideoMapper videoMapper;

    /** 视频记录落库依赖。 */
    @Mock
    private VideoRecordWriter videoRecordWriter;

    /** 播放凭证读写依赖。 */
    @Mock
    private VideoPlayTicketService videoPlayTicketService;

    /** 当前操作人解析依赖。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 视频模块配置，目录指向临时目录。 */
    private VideoProperties videoProperties;

    /** 被测服务。 */
    private VideoServiceImpl videoService;

    /**
     * 每个用例前构造被测服务，视频目录指向临时目录。
     */
    @BeforeEach
    void setUp() {
        videoProperties = new VideoProperties();
        videoProperties.setDirectory(tempDir.toString());
        videoService = new VideoServiceImpl(videoProperties, videoMapper, videoRecordWriter, videoPlayTicketService,
                currentOperatorService);
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
    }

    /**
     * 合法 mp4 上传：落盘到视频目录、写库字段正确、返回视图对象不暴露保存文件名。
     */
    @Test
    void uploadValidMp4WritesFileAndInsertsRecord() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "demo.MP4", "video/mp4", mp4Bytes());

        VideoVO result = videoService.upload(file);

        ArgumentCaptor<VideoEntity> captor = ArgumentCaptor.forClass(VideoEntity.class);
        verify(videoRecordWriter).insert(captor.capture());
        VideoEntity entity = captor.getValue();
        assertThat(entity.getContentType()).isEqualTo("video/mp4");
        assertThat(entity.getStoredFileName()).endsWith(".mp4");
        assertThat(entity.getFileSize()).isEqualTo(file.getSize());
        assertThat(entity.getStatus()).isEqualTo(VideoStatus.NORMAL);
        assertThat(entity.getCreateBy()).isEqualTo("1");
        assertThat(Files.readAllBytes(tempDir.resolve(entity.getStoredFileName()))).isEqualTo(mp4Bytes());
        assertThat(result.getOriginalFileName()).isEqualTo("demo.MP4");
        assertThat(result.getContentType()).isEqualTo("video/mp4");
    }

    /**
     * webm 与 ogv 按各自魔数通过校验，内容类型按后缀确定。
     */
    @Test
    void uploadValidWebmAndOgv() {
        byte[] webm = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] ogg = "OggS\0\0\0\0\0\0\0\0".getBytes(StandardCharsets.US_ASCII);

        assertThat(videoService.upload(new MockMultipartFile("file", "a.webm", null, webm)).getContentType())
                .isEqualTo("video/webm");
        assertThat(videoService.upload(new MockMultipartFile("file", "b.ogv", null, ogg)).getContentType())
                .isEqualTo("video/ogg");
    }

    /**
     * 后缀不在白名单内时拒绝，不落盘不写库。
     */
    @Test
    void uploadRejectsUnsupportedSuffix() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "demo.avi", null, mp4Bytes());

        assertThatThrownBy(() -> videoService.upload(file)).isInstanceOf(InvalidVideoFileException.class);
        verify(videoRecordWriter, never()).insert(any());
        assertThat(listFiles()).isEmpty();
    }

    /**
     * 文本文件改后缀伪装为 mp4 时魔数校验失败。
     */
    @Test
    void uploadRejectsFakeMp4() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "fake.mp4", null,
                "this is not a video file".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> videoService.upload(file)).isInstanceOf(InvalidVideoFileException.class);
        verify(videoRecordWriter, never()).insert(any());
        assertThat(listFiles()).isEmpty();
    }

    /**
     * 超过大小上限时拒绝，提示信息包含可读上限。
     */
    @Test
    void uploadRejectsOversizedFile() {
        videoProperties.setMaxFileSize(DataSize.ofBytes(8));
        MockMultipartFile file = new MockMultipartFile("file", "big.mp4", null, mp4Bytes());

        assertThatThrownBy(() -> videoService.upload(file)).isInstanceOf(VideoFileTooLargeException.class);
        verify(videoRecordWriter, never()).insert(any());
    }

    /**
     * 写库失败时删除已落盘文件，并把异常继续抛出。
     */
    @Test
    void uploadCleansUpFileWhenInsertFails() throws IOException {
        doThrow(new IllegalStateException("db down")).when(videoRecordWriter).insert(any());
        MockMultipartFile file = new MockMultipartFile("file", "demo.mp4", null, mp4Bytes());

        assertThatThrownBy(() -> videoService.upload(file)).isInstanceOf(IllegalStateException.class);
        assertThat(listFiles()).isEmpty();
    }

    /**
     * 删除已删除的视频返回"视频不存在"。
     */
    @Test
    void deleteAlreadyDeletedVideoThrowsNotFound() {
        when(videoMapper.selectById(5L)).thenReturn(entity(5L, VideoStatus.DELETED, "x.mp4"));

        assertThatThrownBy(() -> videoService.delete(5L)).isInstanceOf(VideoNotFoundException.class);
        verify(videoMapper, never()).updateById(any(VideoEntity.class));
    }

    /**
     * 删除正常视频只更新状态为已删除，不删除磁盘文件。
     */
    @Test
    void deleteMarksVideoDeleted() {
        VideoEntity entity = entity(6L, VideoStatus.NORMAL, "x.mp4");
        when(videoMapper.selectById(6L)).thenReturn(entity);

        videoService.delete(6L);

        verify(videoMapper).updateById(entity);
        assertThat(entity.getStatus()).isEqualTo(VideoStatus.DELETED);
        assertThat(entity.getUpdateBy()).isEqualTo("1");
    }

    /**
     * 批量删除空列表直接返回 0，不访问数据库。
     */
    @Test
    void batchDeleteEmptyIdsReturnsZero() {
        VideoBatchDeleteRequest request = new VideoBatchDeleteRequest();
        request.setIds(List.of());

        assertThat(videoService.batchDelete(request)).isZero();
        verify(videoMapper, never()).update(any(), any());
    }

    /**
     * 正常视频签发播放凭证；已删除视频拒绝签发。
     */
    @Test
    void createPlayTicketOnlyForNormalVideo() {
        when(videoMapper.selectById(7L)).thenReturn(entity(7L, VideoStatus.NORMAL, "x.mp4"));
        when(videoMapper.selectById(8L)).thenReturn(entity(8L, VideoStatus.DELETED, "y.mp4"));
        when(videoPlayTicketService.issue(7L)).thenReturn("ticket-7");
        when(videoPlayTicketService.ttlSeconds()).thenReturn(600L);

        VideoPlayTicketVO ticket = videoService.createPlayTicket(7L);

        assertThat(ticket.getTicket()).isEqualTo("ticket-7");
        assertThat(ticket.getExpiresInSeconds()).isEqualTo(600L);
        assertThatThrownBy(() -> videoService.createPlayTicket(8L)).isInstanceOf(VideoNotFoundException.class);
        verify(videoPlayTicketService, never()).issue(8L);
    }

    /**
     * 凭证校验失败时拒绝播放，且不查询数据库。
     */
    @Test
    void loadStreamRejectsInvalidTicket() {
        when(videoPlayTicketService.verifyAndRenew("bad", 9L)).thenReturn(false);

        assertThatThrownBy(() -> videoService.loadStream(9L, "bad")).isInstanceOf(InvalidPlayTicketException.class);
        verify(videoMapper, never()).selectById(anyLong());
    }

    /**
     * 凭证有效但视频已删除时拒绝播放。
     */
    @Test
    void loadStreamRejectsDeletedVideo() {
        when(videoPlayTicketService.verifyAndRenew("ok", 10L)).thenReturn(true);
        when(videoMapper.selectById(10L)).thenReturn(entity(10L, VideoStatus.DELETED, "x.mp4"));

        assertThatThrownBy(() -> videoService.loadStream(10L, "ok")).isInstanceOf(InvalidPlayTicketException.class);
    }

    /**
     * 凭证有效且视频正常时返回磁盘文件资源与内容类型。
     */
    @Test
    void loadStreamReturnsFileResource() throws IOException {
        Files.write(tempDir.resolve("stored.mp4"), mp4Bytes());
        when(videoPlayTicketService.verifyAndRenew(eq("ok"), eq(11L))).thenReturn(true);
        when(videoMapper.selectById(11L)).thenReturn(entity(11L, VideoStatus.NORMAL, "stored.mp4"));

        VideoStreamResource stream = videoService.loadStream(11L, "ok");

        assertThat(stream.getContentType()).isEqualTo("video/mp4");
        assertThat(stream.getResource().contentLength()).isEqualTo(mp4Bytes().length);
    }

    /**
     * 魔数判定：文件头不足长度时不匹配。
     */
    @Test
    void matchesMagicHandlesShortHeader() {
        assertThat(VideoServiceImpl.matchesMagic(".mp4", new byte[] {0, 0, 0})).isFalse();
        assertThat(VideoServiceImpl.matchesMagic(".webm", null)).isFalse();
        assertThat(VideoServiceImpl.matchesMagic(".mp4", mp4Bytes())).isTrue();
    }

    /**
     * 构造一个最小的 mp4 文件头（box 长度 + ftyp + 品牌）。
     *
     * @return mp4 文件头字节
     */
    private static byte[] mp4Bytes() {
        return new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 1};
    }

    /**
     * 构造视频记录实体。
     *
     * @param id             记录 id
     * @param status         状态
     * @param storedFileName 磁盘保存文件名
     * @return 视频记录实体
     */
    private static VideoEntity entity(Long id, int status, String storedFileName) {
        return VideoEntity.builder()
                .id(id)
                .originalFileName("origin.mp4")
                .storedFileName(storedFileName)
                .contentType("video/mp4")
                .fileSize(16L)
                .status(status)
                .build();
    }

    /**
     * 列出临时目录下的全部文件。
     *
     * @return 文件路径列表
     * @throws IOException 读取目录失败
     */
    private List<Path> listFiles() throws IOException {
        try (Stream<Path> files = Files.list(tempDir)) {
            return files.toList();
        }
    }
}
