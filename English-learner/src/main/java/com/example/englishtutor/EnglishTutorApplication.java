package com.example.englishtutor;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class EnglishTutorApplication {

  /**
   * 全局时区固定为 Asia/Shanghai（东八区）。
   * 容器（Alpine）默认 UTC，LocalDateTime.now() 会拿到 UTC 时间，导致注册时间等偏差 8 小时；
   * 在 main 里、Spring 启动前就把 JVM 默认时区设死，所有 LocalDateTime.now() 一律走东八区。
   */
  public static void main(String[] args) {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    SpringApplication.run(EnglishTutorApplication.class, args);
  }

  /** 兜底：即使绕过 main 直接拉起容器，也在 Bean 初始化阶段再设一次 */
  @PostConstruct
  void initTimeZone() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
  }
}
