package com.example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

@Configuration
@PropertySource(value = "classpath:application-local.properties", ignoreResourceNotFound = true)
public class KiteConfig {

    private final Environment environment;

    public KiteConfig(Environment environment) {
        this.environment = environment;
    }

    public String getApiKey() {
        String envValue = environment.getProperty("KITE_API_KEY");
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return environment.getProperty("kite.api.key", "");
    }

    public String getApiSecret() {
        String envValue = environment.getProperty("KITE_API_SECRET");
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return environment.getProperty("kite.api.secret", "");
    }

    public String getTelegramBotToken() {
        String envValue = environment.getProperty("TELEGRAM_BOT_TOKEN");
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return environment.getProperty("telegram.bot.token", "");
    }

    public String getTelegramChatId() {
        String envValue = environment.getProperty("TELEGRAM_CHAT_ID");
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return environment.getProperty("telegram.chat.id", "");
    }
}
