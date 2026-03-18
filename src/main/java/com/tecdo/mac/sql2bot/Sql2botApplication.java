package com.tecdo.mac.sql2bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Sql2botApplication {

    public static void main(String[] args) {
        SpringApplication.run(Sql2botApplication.class, args);
    }

}
