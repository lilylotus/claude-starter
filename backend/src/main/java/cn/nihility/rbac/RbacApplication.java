package cn.nihility.rbac;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类。{@code @EnableScheduling} 支撑 {@code cn.nihility.rbac.sync.sign.NonceStore}
 * 定期清理过期 nonce 登记的定时任务（app-sync-notify-pull-api change design.md Decision 10）。
 */
@SpringBootApplication
@MapperScan(basePackages = "cn.nihility.rbac", annotationClass = Mapper.class)
@EnableScheduling
public class RbacApplication {

    public static void main(String[] args) {
        SpringApplication.run(RbacApplication.class, args);
    }

}
