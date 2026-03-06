package com.clickchecker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class ClickCheckerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClickCheckerApplication.class, args);
    }

}
