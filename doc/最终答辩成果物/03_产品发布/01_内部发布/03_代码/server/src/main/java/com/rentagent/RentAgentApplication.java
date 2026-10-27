package com.rentagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class RentAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentAgentApplication.class, args);
    }
}
