package com.fandrops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.fandrops")
public class FandropsApplication {

    public static void main(String[] args) {

        SpringApplication.run(FandropsApplication.class, args);

    }
}