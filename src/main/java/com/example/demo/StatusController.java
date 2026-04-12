package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    private static final Logger log = LoggerFactory.getLogger(StatusController.class);
    private final TelegramService telegramService;

    public StatusController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @GetMapping("/api-status")
    public String status() {
        try {
            telegramService.sendMessage("✅ TradingBot is live and running.");
            log.info("Status check OK, Telegram test message sent");
            return "Success";
        } catch (Exception e) {
            log.error("Status check failed: {}", e.getMessage());
            return "Error";
        }
    }
}