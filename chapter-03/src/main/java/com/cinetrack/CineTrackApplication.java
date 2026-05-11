package com.cinetrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableJpaAuditing
@EnableRetry
public class CineTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(CineTrackApplication.class, args);
    }
}
