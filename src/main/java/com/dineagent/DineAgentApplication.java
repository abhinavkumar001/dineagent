package com.dineagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DineAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(DineAgentApplication.class, args);
    }
}
