package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.zip.GZIPInputStream;

@Service
public class InstrumentSyncService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentSyncService.class);
    private static final String INSTRUMENTS_URL = "https://api.kite.trade/instruments";
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final TokenStore tokenStore;

    public InstrumentSyncService(JdbcTemplate jdbcTemplate, TokenStore tokenStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenStore = tokenStore;
    }

    public SyncResult syncInstruments() throws Exception {
        log.info("Starting instrument sync...");
        log.debug("Auth header: {}", tokenStore.getAuthHeader());

        // Fetch gzipped CSV from Kite API
        String authHeader = tokenStore.getAuthHeader();
        URI uri = new URI(INSTRUMENTS_URL);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestProperty("X-Kite-Version", "3");
        conn.setRequestProperty("Authorization", authHeader);

        log.info("Request URL: {}", INSTRUMENTS_URL);
        log.info("Request headers - X-Kite-Version: 3, Authorization: {}", authHeader);

        int statusCode = conn.getResponseCode();
        log.info("Response status code: {}", statusCode);

        if (statusCode != 200) {
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder errorBody = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorBody.append(line);
            }
            log.error("API error response: {}", errorBody.toString());
            throw new Exception("Kite API returned status " + statusCode + ": " + errorBody.toString());
        }

        String contentEncoding = conn.getHeaderField("Content-Encoding");
        log.info("Content-Encoding header: {}", contentEncoding);

        InputStream inputStream = conn.getInputStream();
        BufferedReader reader;

        if ("gzip".equals(contentEncoding)) {
            log.info("Decompressing gzip response");
            reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(inputStream)));
        } else {
            log.info("Response is not gzipped, reading as plain text");
            reader = new BufferedReader(new InputStreamReader(inputStream));
        }

        int successCount = 0;
        int errorCount = 0;

        try (reader) {
            String line;
            boolean isHeader = true;
            java.util.List<Object[]> batch = new java.util.ArrayList<>();

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    log.debug("Header line: {}", line);
                    continue;
                }

                String[] cols = parseCSVLine(line);
                if (cols.length < 12) {
                    log.warn("Row has insufficient columns ({}): {}", cols.length, line);
                    errorCount++;
                    continue;
                }

                try {
                    String instrumentToken = cols[0];
                    String exchangeToken = cols[1];
                    String tradingsymbol = cols[2];
                    String name = cols[3];
                    String lastPriceStr = cols[4].isEmpty() ? "0" : cols[4];
                    java.sql.Date expiry = cols[5].isEmpty() ? null : java.sql.Date.valueOf(cols[5]);
                    String strikeStr = cols[6].isEmpty() ? "0" : cols[6];
                    String tickSizeStr = cols[7].isEmpty() ? "0" : cols[7];
                    String lotSizeStr = cols[8].isEmpty() ? "1" : cols[8];
                    String instrumentType = cols[9];
                    String segment = cols[10];
                    String exchange = cols[11];

                    batch.add(new Object[]{
                            instrumentToken, exchangeToken, tradingsymbol, name,
                            Double.parseDouble(lastPriceStr), expiry,
                            Double.parseDouble(strikeStr), Double.parseDouble(tickSizeStr),
                            Integer.parseInt(lotSizeStr), instrumentType, segment, exchange
                    });

                    if (batch.size() >= BATCH_SIZE) {
                        executeBatch(batch);
                        successCount += batch.size();
                        log.info("Synced {} instruments so far (success: {}, errors: {})", successCount, successCount, errorCount);
                        batch.clear();
                    }
                } catch (Exception e) {
                    log.warn("Skipping row: {}, error: {}", line, e.getMessage());
                    errorCount++;
                }
            }

            // Insert remaining records
            if (!batch.isEmpty()) {
                executeBatch(batch);
                successCount += batch.size();
            }

            // Purge inactive instruments — no stock_history in last 30 days
            int deleted = jdbcTemplate.update(
                "DELETE FROM instruments " +
                "WHERE exchange = 'BSE' AND instrument_type = 'EQ' " +
                "AND instrument_token NOT IN (" +
                "  SELECT DISTINCT symbol FROM stock_history " +
                "  WHERE trade_date >= CURRENT_DATE - INTERVAL '30 days'" +
                ")");
            log.info("Purged {} inactive BSE EQ instruments", deleted);

            log.info("Instrument sync complete. Total success: {}, errors: {}", successCount, errorCount);
            return new SyncResult(successCount, errorCount, "COMPLETED",
                String.format("Synced %d instruments with %d errors, purged %d inactive", successCount, errorCount, deleted));
        }
    }

    private void executeBatch(java.util.List<Object[]> batch) {
        String sql = "INSERT INTO instruments (instrument_token, exchange_token, tradingsymbol, name, last_price, expiry, strike, tick_size, lot_size, instrument_type, segment, exchange) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (instrument_token) DO UPDATE SET " +
                "last_price = EXCLUDED.last_price, updated_at = CURRENT_TIMESTAMP";

        int[][] batchResults = jdbcTemplate.batchUpdate(sql, batch, BATCH_SIZE,
                (ps, argument) -> {
                    ps.setString(1, (String) argument[0]);
                    ps.setString(2, (String) argument[1]);
                    ps.setString(3, (String) argument[2]);
                    ps.setString(4, (String) argument[3]);
                    ps.setDouble(5, (Double) argument[4]);
                    ps.setObject(6, argument[5]);
                    ps.setDouble(7, (Double) argument[6]);
                    ps.setDouble(8, (Double) argument[7]);
                    ps.setInt(9, (Integer) argument[8]);
                    ps.setString(10, (String) argument[9]);
                    ps.setString(11, (String) argument[10]);
                    ps.setString(12, (String) argument[11]);
                });

        log.debug("Batch insert completed. Rows affected: {}", batchResults.length);
    }

    private String[] parseCSVLine(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }
}
