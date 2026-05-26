package com.safemap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // Habilita o @Scheduled no PasswordResetService (limpeza de tokens)
public class SafeMapApplication {
    public static void main(String[] args) {
        SpringApplication.run(SafeMapApplication.class, args);
    }
}
