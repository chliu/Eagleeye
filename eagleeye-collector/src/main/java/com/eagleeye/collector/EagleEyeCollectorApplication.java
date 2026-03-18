package com.eagleeye.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@SpringBootApplication(scanBasePackages = "com.eagleeye")
@EnableScheduling
public class EagleEyeCollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EagleEyeCollectorApplication.class, args);
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
