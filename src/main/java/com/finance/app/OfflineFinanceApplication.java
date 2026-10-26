package com.finance.app;

import com.finance.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.finance"})
@EnableJpaRepositories(basePackages = {
        "com.finance.repository", "com.finance.financeplus.repository", "com.finance.features.repository"
})
@EntityScan(basePackages = {
        "com.finance.model.entity", "com.finance.financeplus.model", "com.finance.features.model"
})
@EnableScheduling
public class OfflineFinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfflineFinanceApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedDefaultAccounts(UserService userService) {
        return args -> {
            if (userService.getUserByUsername("admin").isEmpty()) {
                userService.registerUser("admin", "admin@finwise.local", "admin123", "System Administrator");
            }
            if (userService.getUserByUsername("admin1").isEmpty()) {
                userService.registerUser("admin1", "admin1@finwise.local", "admin1234", "Default User");
            }
        };
    }
}
