package com.ming.sspexchange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SspExchangeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SspExchangeApplication.class, args);
    }

}
