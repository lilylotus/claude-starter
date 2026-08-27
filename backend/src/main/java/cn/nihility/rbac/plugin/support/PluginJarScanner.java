package cn.nihility.rbac.plugin.support;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * 插件目录扫描器（plugin-jar-management capability spec "插件目录扫描与发现"）：枚举指定
 * 目录下的 {@code .jar} 文件，非 {@code .jar} 文件或子目录忽略并记录日志；目录不存在时记录
 * 提示信息并返回空列表，不影响主程序正常启动。
 * <p>
 * 本类只负责"发现"，返回结果按文件名字典序排列，作为后续按 {@code priority} 重新排序
 * （design.md Decision 4）前的确定性基线；真正决定处理顺序的排序逻辑在
 * {@code PluginBeanDefinitionRegistrar} 中结合 {@link PluginMetadataReader} 解析出的
 * 优先级完成。
 */
@Slf4j
public final class PluginJarScanner {

    /** {@code .jar} 后缀。 */
    private static final String JAR_SUFFIX = ".jar";

    /**
     * 工具类不允许实例化。
     */
    private PluginJarScanner() {
    }

    /**
     * 扫描插件目录，返回其中全部 {@code .jar} 文件路径，按文件名字典序排列。
     *
     * @param directory 插件目录
     * @return {@code .jar} 文件路径列表，目录不存在或为空时返回空列表
     */
    public static List<Path> scan(Path directory) {
        if (!Files.exists(directory)) {
            log.info("插件目录 [{}] 不存在，跳过插件加载流程", directory);
            return List.of();
        }
        if (!Files.isDirectory(directory)) {
            log.warn("插件目录 [{}] 不是一个目录，跳过插件加载流程", directory);
            return List.of();
        }
        List<Path> jarFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path candidate : stream) {
                if (Files.isDirectory(candidate)) {
                    log.info("忽略插件目录下的子目录 [{}]", candidate.getFileName());
                    continue;
                }
                String fileName = candidate.getFileName().toString();
                if (!fileName.toLowerCase(Locale.ROOT).endsWith(JAR_SUFFIX)) {
                    log.info("忽略插件目录下的非 jar 文件 [{}]", fileName);
                    continue;
                }
                jarFiles.add(candidate);
            }
        } catch (IOException ex) {
            log.warn("扫描插件目录 [{}] 失败：{}，跳过插件加载流程", directory, ex.getMessage());
            return List.of();
        }
        jarFiles.sort(Comparator.comparing(path -> path.getFileName().toString()));
        log.info("插件目录 [{}] 发现 {} 个候选 jar 包：{}", directory, jarFiles.size(), jarFiles);
        return jarFiles;
    }
}
