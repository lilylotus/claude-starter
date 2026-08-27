package cn.nihility.rbac.plugin.support.fixture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试用"主程序" Controller，供插件路径冲突/Controller 覆盖测试使用。
 */
@RestController
@RequestMapping("/fixture/main")
public class FakeMainController {

    /**
     * 主程序默认实现。
     *
     * @return 标识文案
     */
    @GetMapping("/hello")
    public String hello() {
        return "main-controller";
    }
}
