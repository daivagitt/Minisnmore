package com.miniandmore.commerce;

import com.miniandmore.commerce.config.ApplicationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ApplicationProperties.class)
public class CommerceApp {

    public static void main(String[] args) {
        SpringApplication.run(CommerceApp.class, args);
    }
}
