package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/stock")
public class DebugController {

    private static final Logger log = LoggerFactory.getLogger(DebugController.class);
    private final TradingService tradingService;
    private final TelegramService telegramService;
    private final AnalyzeStatusHolder statusHolder;

    public DebugController(TradingService tradingService, TelegramService telegramService, AnalyzeStatusHolder statusHolder) {
        this.tradingService = tradingService;
        this.telegramService = telegramService;
        this.statusHolder = statusHolder;
    }

    @PostMapping("/analyze")
    public String runAnalysis() {
        if (statusHolder.isRunning()) {
            return "Analyze already running. Check /stock/analyze-status";
        }

        try {
            statusHolder.start();
        } catch (IllegalStateException e) {
            return "Analyze already running. Check /stock/analyze-status";
        }

        new Thread(() -> {
            try {
                statusHolder.markRunning();
                SyncResult result = tradingService.syncData();
                log.info("Stock analysis completed: {}", result);
                telegramService.sendDropReport();
                statusHolder.finish();
            } catch (Exception e) {
                log.error("Stock analysis failed: {}", e.getMessage());
                statusHolder.finish();
            }
        }).start();
        return "Analyzing started. Please check /stock/analyze-status";
    }

    @GetMapping("/analyze-status")
    public Map<String, Object> getAnalyzeStatus() {
        return statusHolder.toMap();
    }
}
