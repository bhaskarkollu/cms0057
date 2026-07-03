package com.cms0057.priorauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class Cms0057Application {

    public static void main(String[] args) {
        SpringApplication.run(Cms0057Application.class, args);
    }
}
