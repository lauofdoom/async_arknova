package com.arknova.bot.config;

import com.arknova.bot.discord.ButtonInteractionListener;
import com.arknova.bot.discord.SlashCommandListener;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ArkNovaProperties.class)
public class JdaConfig {

  private static final Logger log = LoggerFactory.getLogger(JdaConfig.class);

  private JDA jda;

  @Bean
  public JDA jda(
      ArkNovaProperties props,
      SlashCommandListener slashCommandListener,
      ButtonInteractionListener buttonInteractionListener)
      throws InterruptedException {

    log.info("Initialising JDA...");

    jda =
        JDABuilder.createDefault(props.discord().token())
            .enableIntents(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.MESSAGE_CONTENT)
            .setMemberCachePolicy(MemberCachePolicy.ONLINE)
            .setActivity(Activity.playing("Ark Nova"))
            .setStatus(OnlineStatus.ONLINE)
            .addEventListeners(slashCommandListener, buttonInteractionListener)
            .build()
            .awaitReady();

    log.info("JDA ready. Connected as: {}", jda.getSelfUser().getAsTag());

    // Register slash commands with Discord (guild-global update)
    jda.updateCommands()
        .addCommands(slashCommandListener.getAllCommandData())
        .queue(
            cmds -> log.info("Registered {} slash command(s) with Discord", cmds.size()),
            err  -> log.error("Failed to register slash commands", err));

    return jda;
  }

  @PreDestroy
  public void shutdown() {
    if (jda != null) {
      log.info("Shutting down JDA...");
      jda.shutdown();
    }
  }
}
