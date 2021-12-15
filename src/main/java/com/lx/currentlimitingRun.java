package com.lx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 使用redis + lua 脚本 实现限流
 * @author ty
 * @create 2021-12-15 14:03
 */
@SpringBootApplication
public class currentlimitingRun {
    public static void main(String[] args) {
        SpringApplication.run(currentlimitingRun.class);
    }
}
