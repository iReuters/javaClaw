package com.javaclaw;

import com.javaclaw.config.Config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(Config.class)
public class JavaclawApplication {
    public static void main(String[] args) {
        SpringApplication.run(JavaclawApplication.class, args);
    }
}