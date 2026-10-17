package com.juejin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.juejin.mapper")
public class JuejinApplication {

    public static void main(String[] args) {
        SpringApplication.run(JuejinApplication.class, args);
    }
}
