package com.arknova.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arknova")
public record ArkNovaProperties(Discord discord, Storage storage, Cards cards, Security security) {

  public record Discord(String token, String clientId, String logChannelId) {}

  public record Storage(String imagePath, String cardImageCachePath) {}

  public record Cards(String resourcePath) {}

  public record Security(String jwtSecret) {}
}
