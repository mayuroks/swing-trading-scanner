package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    public void syncData() throws IOException {
        String authHeader = tokenStore.getAuthHeader();

        for (String token : AppConstants.INSTRUMENT_TOKENS) {
            String url = String.format(
                    "https://api.kite.trade/instruments/historical/%s/day?from=2026-04-01+09:15:00&to=2026-04-12+09:15:00",
                    token);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Kite-Version", "3");
            headers.set("Authorization", authHeader);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            log.info("[{}] Raw response: {}", token, response.getBody()); // 1. see what the API actually returned

            // 2. Parse and Save to DB
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode candles = root.path("data").path("candles");
            log.info("[{}] Candle count: {}", token, candles.size()); // 2. confirm candles were found

            for (JsonNode candle : candles) {
                String date = candle.get(0) != null ? candle.get(0).asText() : "NULL";
                String close = candle.get(4) != null ? candle.get(4).asText() : "NULL";
                log.info("[{}] Inserting — date: {}, close: {}", token, date, close); // 3. spot bad values before insert

                LocalDate tradeDate = LocalDate.parse(date.split("T")[0]);
                jdbcTemplate.update(
                        "INSERT INTO stock_history (symbol, trade_date, close_price) VALUES (?, ?, ?) " +
                                "ON CONFLICT (symbol, trade_date) " +
                                "DO UPDATE SET close_price = EXCLUDED.close_price",
                        token,
                        tradeDate,
                        candle.get(4).asDouble());
            }
        }
    }

    public String generateSignalsJson() throws IOException {
        // Query the view we created earlier
        List<Map<String, Object>> results = jdbcTemplate.queryForList("SELECT * FROM v_stock_analysis");
        File file = new File("signals.json");
        objectMapper.writeValue(file, results);
        return file.getAbsolutePath();
    }
}