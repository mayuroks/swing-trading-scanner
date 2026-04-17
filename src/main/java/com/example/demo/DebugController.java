package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stock")
public class DebugController {

    private static final Logger log = LoggerFactory.getLogger(DebugController.class);
    private final TradingService tradingService;
    private final TelegramService telegramService;

    public DebugController(TradingService tradingService, TelegramService telegramService) {
        this.tradingService = tradingService;
        this.telegramService = telegramService;
    }

    @PostMapping("/analyze")
    public String runAnalysis() {
        new Thread(() -> {
            try {
                SyncResult result = tradingService.syncData();
                log.info("Stock analysis completed: {}", result);
                telegramService.sendDropReport();
            } catch (Exception e) {
                log.error("Stock analysis failed: {}", e.getMessage());
            }
        }).start();
        return "Analyzing started. Please check again in a minute";
    }
}