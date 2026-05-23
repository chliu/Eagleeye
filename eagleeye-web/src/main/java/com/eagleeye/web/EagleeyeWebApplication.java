package com.eagleeye.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.eagleeye")
public class EagleeyeWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(EagleeyeWebApplication.class, args);
    }
}
