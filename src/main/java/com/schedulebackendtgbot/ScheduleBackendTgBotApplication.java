package com.schedulebackendtgbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScheduleBackendTgBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduleBackendTgBotApplication.class, args);
    }

}
