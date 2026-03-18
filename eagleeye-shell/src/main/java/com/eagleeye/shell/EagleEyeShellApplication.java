package com.eagleeye.shell;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.eagleeye")
public class EagleEyeShellApplication {

    public static void main(String[] args) {
        SpringApplication.run(EagleEyeShellApplication.class, args);
    }
}
