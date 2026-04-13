package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/equity")
public class EquityController {

    private final InstrumentSyncService instrumentSyncService;

    public EquityController(InstrumentSyncService instrumentSyncService) {
        this.instrumentSyncService = instrumentSyncService;
    }

    @GetMapping("/refresh")
    public String refreshInstruments() {
        new Thread(() -> {
            try {
                instrumentSyncService.syncInstruments();
            } catch (Exception e) {
                // Synced logged in service
            }
        }).start();
        return "Refreshing in progress. Please check back in a minute";
    }
}
