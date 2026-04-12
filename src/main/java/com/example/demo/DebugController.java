package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/debug")
public class DebugController {

    private final TradingService tradingService;

    public DebugController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @GetMapping("/run-analysis")
    public String runAnalysis() {
        try {
            // First, fetch fresh data and put it in DB
            tradingService.syncData();

            // Then, generate the JSON from the View
            String path = tradingService.generateSignalsJson();
            return "Data synced and JSON Generated at: " + path;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}