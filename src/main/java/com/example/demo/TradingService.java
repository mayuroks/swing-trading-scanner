package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;

@Service
public class TradingService {

    private static final Logger log = LoggerFactory.getLogger(TradingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TokenStore tokenStore;
    private final RestTemplate restTemplate = new RestTemplate();

    public TradingService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, TokenStore tokenStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.tokenStore = tokenStore;
    }

    public SyncResult syncData() throws IOException {
        String authHeader = tokenStore.getAuthHeader();
        int successCount = 0;
        int errorCount = 0;

        // Skip if today is weekend
        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        if (!AppConstants.SKIP_WEEKEND_CHECK && (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
            return new SyncResult(0, 0, "SKIPPED", "Market closed on " + dayOfWeek);
        }

        // Load EQ instruments: test mode or production
        java.util.List<String> tokens;
        if (AppConstants.TEST_INSTRUMENTS_LIST != null && !AppConstants.TEST_INSTRUMENTS_LIST.isEmpty()) {
            tokens = java.util.Arrays.asList(AppConstants.TEST_INSTRUMENTS_LIST.split(","));
        } else {
            String sql = "SELECT instrument_token FROM instruments WHERE exchange = 'BSE' AND instrument_type = 'EQ' ORDER BY instrument_token";
            tokens = jdbcTemplate.queryForList(sql, String.class);
        }

        if (tokens.isEmpty()) {
            log.warn("No BSE EQ instruments found. Run POST /stock/refresh first");
            return new SyncResult(0, 0, "SKIPPED", "No EQ instruments in database. Run refresh first.");
        }

        // Process in batches of 1000 (Kite API limit)
        final int BATCH_SIZE_TOKENS = 1000;
        for (int i = 0; i < tokens.size(); i += BATCH_SIZE_TOKENS) {
            int endIndex = Math.min(i + BATCH_SIZE_TOKENS, tokens.size());
            java.util.List<String> batch = tokens.subList(i, endIndex);

            for (String token : batch) {
            try {
                // Incremental sync: skip if already have today's data
                Integer existingCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM stock_history WHERE symbol = ? AND trade_date = ?",
                        Integer.class,
                        token,
                        today);
                if (existingCount != null && existingCount > 0) {
                    successCount++;
                    continue;
                }

                // Rate limiting: 3 requests/second (Kite limit)
                Thread.sleep(350);

                String url = String.format(
                        "https://api.kite.trade/instruments/historical/%s/day?from=2026-04-01+09:15:00&to=%s+09:15:00",
                        token, today);

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Kite-Version", "3");
                headers.set("Authorization", authHeader);
                HttpEntity<String> entity = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                // Parse and Save to DB
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candles = root.path("data").path("candles");

                int insertCount = 0;
                for (JsonNode candle : candles) {
                    try {
                        String date = candle.get(0) != null ? candle.get(0).asText() : "NULL";

                        LocalDate tradeDate = LocalDate.parse(date.split("T")[0]);
                        jdbcTemplate.update(
                                "INSERT INTO stock_history (symbol, trade_date, close_price, volume) VALUES (?, ?, ?, ?) " +
                                        "ON CONFLICT (symbol, trade_date) " +
                                        "DO UPDATE SET close_price = EXCLUDED.close_price, volume = EXCLUDED.volume",
                                token,
                                tradeDate,
                                candle.get(4).asDouble(),
                                candle.get(5).asLong());
                        insertCount++;
                    } catch (Exception e) {
                        log.warn("[{}] Error inserting candle: {}", token, e.getMessage());
                        errorCount++;
                    }
                }

                successCount += insertCount;

            } catch (Exception e) {
                log.error("[{}] Error fetching/processing data: {}", token, e.getMessage());
                errorCount++;
            }
            }
        }

        return new SyncResult(successCount, errorCount, "COMPLETED",
            String.format("Synced %d candles with %d errors", successCount, errorCount));
    }

}