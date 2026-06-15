package com.silkroad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SilkRoadApplication {
    public static void main(String[] args) {
        SpringApplication.run(SilkRoadApplication.class, args);
    }
}
