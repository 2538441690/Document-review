package com.example.compliance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ComplianceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplianceApplication.class, args);
    }
}