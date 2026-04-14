package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stock")
public class EquityController {

    private static final Logger log = LoggerFactory.getLogger(EquityController.class);
    private final InstrumentSyncService instrumentSyncService;

    public EquityController(InstrumentSyncService instrumentSyncService) {
        this.instrumentSyncService = instrumentSyncService;
    }

    @PostMapping("/refresh")
    public String refreshInstruments() {
        new Thread(() -> {
            try {
                SyncResult result = instrumentSyncService.syncInstruments();
                log.info("Instrument refresh completed: {}", result);
            } catch (Exception e) {
                log.error("Instrument refresh failed: {}", e.getMessage());
            }
        }).start();
        return "Refreshing instrument data in progress. Please check back in a minute";
    }
}
