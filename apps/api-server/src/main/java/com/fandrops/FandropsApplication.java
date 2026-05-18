package com.fandrops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.fandrops")
public class FandropsApplication {

    public static void main(String[] args) {

        SpringApplication.run(FandropsApplication.class, args);

    }
}