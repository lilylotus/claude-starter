package cn.nihility.rbac.captcha.support;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * 基于 JDK 内置 {@code java.awt}（{@link BufferedImage}/{@link Graphics2D}）把验证码文本
 * 渲染成图片并输出为带 {@code data:image/<type>;base64,} 前缀的完整 data URI，附加基础
 * 干扰线/噪点（add-captcha-rate-limit change design.md Decision 1，不引入第三方验证码库）。
 */
@Component
public class CaptchaImageRenderer {

    /** 输出图片格式。 */
    private static final String IMAGE_FORMAT = "png";

    /** 图片背景色。 */
    private static final Color BACKGROUND_COLOR = Color.WHITE;

    /** 干扰线/噪点/文本可选颜色，避免全部内容使用同一种颜色导致对比度过低。 */
    private static final Color[] INK_COLORS = {
            new Color(30, 90, 180), new Color(180, 60, 60), new Color(50, 140, 80),
            new Color(150, 100, 30), new Color(100, 60, 150)
    };

    /** 干扰线条数。 */
    private static final int INTERFERENCE_LINE_COUNT = 5;

    /** 噪点个数。 */
    private static final int NOISE_DOT_COUNT = 40;

    /**
     * 把展示文本渲染成指定宽高的验证码图片，返回带 {@code data:image/<type>;base64,} 前缀的
     * 完整 data URI 字符串（{@code <type>} 由 {@link #IMAGE_FORMAT} 推导，当前为 {@code png}），
     * 前端可不做拼接直接作为 {@code <img>} 的 {@code src}。
     *
     * @param displayText 需要渲染的展示文本（字符模式的随机字符串，或算式模式的算式文本）
     * @param width       图片宽度（像素）
     * @param height      图片高度（像素）
     * @return 带 {@code data:image/png;base64,} 前缀的完整 data URI
     */
    public String renderToBase64(String displayText, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(BACKGROUND_COLOR);
            graphics.fillRect(0, 0, width, height);

            drawNoiseDots(graphics, width, height);
            drawInterferenceLines(graphics, width, height);
            drawText(graphics, displayText, width, height);
        } finally {
            graphics.dispose();
        }
        return toBase64(image);
    }

    /**
     * 绘制随机噪点，做基础干扰。
     *
     * @param graphics 图形上下文
     * @param width    图片宽度
     * @param height   图片高度
     */
    private void drawNoiseDots(Graphics2D graphics, int width, int height) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < NOISE_DOT_COUNT; i++) {
            graphics.setColor(randomInkColor(random));
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            graphics.fillRect(x, y, 1, 1);
        }
    }

    /**
     * 绘制随机干扰线，做基础干扰。
     *
     * @param graphics 图形上下文
     * @param width    图片宽度
     * @param height   图片高度
     */
    private void drawInterferenceLines(Graphics2D graphics, int width, int height) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < INTERFERENCE_LINE_COUNT; i++) {
            graphics.setColor(randomInkColor(random));
            graphics.setStroke(new BasicStroke(1F));
            int x1 = random.nextInt(width);
            int y1 = random.nextInt(height);
            int x2 = random.nextInt(width);
            int y2 = random.nextInt(height);
            graphics.drawLine(x1, y1, x2, y2);
        }
    }

    /**
     * 把展示文本居中绘制到图片上，字体大小按图片高度自适应。
     *
     * @param graphics    图形上下文
     * @param displayText 展示文本
     * @param width       图片宽度
     * @param height      图片高度
     */
    private void drawText(Graphics2D graphics, String displayText, int width, int height) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int fontSize = Math.max(12, (int) (height * 0.6));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
        var metrics = graphics.getFontMetrics();
        int textWidth = metrics.stringWidth(displayText);
        int x = Math.max(2, (width - textWidth) / 2);
        int y = (height + metrics.getAscent() - metrics.getDescent()) / 2;
        graphics.setColor(randomInkColor(random));
        graphics.drawString(displayText, x, y);
    }

    /**
     * 从 {@link #INK_COLORS} 中随机取一种颜色。
     *
     * @param random 随机数生成器
     * @return 随机颜色
     */
    private Color randomInkColor(ThreadLocalRandom random) {
        return INK_COLORS[random.nextInt(INK_COLORS.length)];
    }

    /**
     * 把图片编码为带 {@code data:image/<type>;base64,} 前缀的完整 data URI，MIME 类型随
     * {@link #IMAGE_FORMAT} 推导，避免上层硬编码导致格式改动时前缀不同步。
     *
     * @param image 待编码图片
     * @return 完整 data URI 字符串
     */
    private String toBase64(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, IMAGE_FORMAT, outputStream);
            String encoded = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            return "data:image/" + IMAGE_FORMAT + ";base64," + encoded;
        } catch (IOException e) {
            throw new IllegalStateException("图形验证码渲染失败", e);
        }
    }
}
