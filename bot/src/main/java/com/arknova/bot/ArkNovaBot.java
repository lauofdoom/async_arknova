package com.arknova.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArkNovaBot {

  public static void main(String[] args) {
    SpringApplication.run(ArkNovaBot.class, args);
  }
}
