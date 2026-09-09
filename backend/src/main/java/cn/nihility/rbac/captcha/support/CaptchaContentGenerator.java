package cn.nihility.rbac.captcha.support;

import cn.nihility.rbac.captcha.config.RbacCaptchaProperties;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按配置生成验证码展示内容与正确答案（add-captcha-rate-limit change tasks.md 1.3）：
 * 字符模式生成候选字符集内的随机字符串，算式模式生成加减乘除算式文本与整数答案。
 */
@Component
@RequiredArgsConstructor
public class CaptchaContentGenerator {

    /**
     * 字符模式候选字符集：{@code 0-9}+{@code A-Z}+{@code a-z} 固定剔除易混淆字符（数字
     * {@code 0}、{@code 1}，字母 {@code O}、{@code o}、{@code I}、{@code i}、{@code l}），
     * 以常量数组维护最终候选集合，不在生成时每次过滤（design.md Decision 3）。
     */
    private static final char[] CANDIDATE_CHARACTERS =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz".toCharArray();

    /** 加减法操作数上界（含），范围 1-50。 */
    private static final int ADD_SUBTRACT_BOUND = 50;

    /** 乘除法操作数上界（含），范围 1-9。 */
    private static final int MULTIPLY_DIVIDE_BOUND = 9;

    /** 图形验证码相关配置。 */
    private final RbacCaptchaProperties captchaProperties;

    /**
     * 按当前配置的验证码类型生成一次内容与答案。
     *
     * @return 本次生成的展示文本与正确答案
     */
    public CaptchaGenerationResult generate() {
        return switch (captchaProperties.getType()) {
            case CHARACTER -> generateCharacterContent();
            case ARITHMETIC -> generateArithmeticContent();
        };
    }

    /**
     * 生成字符模式内容：从候选字符集中随机挑选指定个数的字符拼接成字符串，展示文本与答案
     * 相同。
     *
     * @return 字符模式生成结果
     */
    private CaptchaGenerationResult generateCharacterContent() {
        int length = captchaProperties.getCharacterLength();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(CANDIDATE_CHARACTERS[random.nextInt(CANDIDATE_CHARACTERS.length)]);
        }
        String content = builder.toString();
        return new CaptchaGenerationResult(content, content);
    }

    /**
     * 生成算式模式内容：随机选择加减乘除其中一种运算符，除法先随机结果与除数、反推被除数
     * 保证整除（design.md Decision 5）。
     *
     * @return 算式模式生成结果
     */
    private CaptchaGenerationResult generateArithmeticContent() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        ArithmeticOperator[] operators = ArithmeticOperator.values();
        ArithmeticOperator operator = operators[random.nextInt(operators.length)];

        int firstOperand;
        int secondOperand;
        int result;
        switch (operator) {
            case ADD -> {
                firstOperand = random.nextInt(1, ADD_SUBTRACT_BOUND + 1);
                secondOperand = random.nextInt(1, ADD_SUBTRACT_BOUND + 1);
                result = firstOperand + secondOperand;
            }
            case SUBTRACT -> {
                firstOperand = random.nextInt(1, ADD_SUBTRACT_BOUND + 1);
                // 被减数不小于减数，保证结果非负，降低心算歧义。
                secondOperand = random.nextInt(1, firstOperand + 1);
                result = firstOperand - secondOperand;
            }
            case MULTIPLY -> {
                firstOperand = random.nextInt(1, MULTIPLY_DIVIDE_BOUND + 1);
                secondOperand = random.nextInt(1, MULTIPLY_DIVIDE_BOUND + 1);
                result = firstOperand * secondOperand;
            }
            case DIVIDE -> {
                // 先随机除数与结果，再反推被除数，保证能整除（不产生小数结果）。
                secondOperand = random.nextInt(1, MULTIPLY_DIVIDE_BOUND + 1);
                result = random.nextInt(1, MULTIPLY_DIVIDE_BOUND + 1);
                firstOperand = secondOperand * result;
            }
            default -> throw new IllegalStateException("不支持的算式运算符：" + operator);
        }

        String displayText = firstOperand + " " + operator.getSymbol() + " " + secondOperand + " = ?";
        return new CaptchaGenerationResult(displayText, String.valueOf(result));
    }

    /**
     * 算式模式支持的四则运算符。
     */
    private enum ArithmeticOperator {

        /** 加法。 */
        ADD("+"),
        /** 减法。 */
        SUBTRACT("-"),
        /** 乘法。 */
        MULTIPLY("*"),
        /** 除法。 */
        DIVIDE("/");

        /** 展示在算式文本中的运算符符号。 */
        private final String symbol;

        ArithmeticOperator(String symbol) {
            this.symbol = symbol;
        }

        String getSymbol() {
            return symbol;
        }
    }
}
