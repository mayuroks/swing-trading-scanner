package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stock")
public class StockController {

    private static final Logger log = LoggerFactory.getLogger(StockController.class);
    private final JdbcTemplate jdbcTemplate;

    public StockController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/indicator")
    public Map<String, Object> analyzeStocks(
            @RequestParam(defaultValue = "down") String trend,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Validate inputs
        if (size > 100) size = 100;
        if (page < 0) page = 0;

        if (!trend.equals("up") && !trend.equals("down")) {
            trend = "down";
        }

        // Determine sort direction and sign comparison
        String sortDir = trend.equals("down") ? "ASC" : "DESC";
        String signOp = trend.equals("down") ? "<" : ">";

        // CTE: get latest trade_date per instrument
        String cte =
            "WITH latest AS (" +
            "  SELECT DISTINCT ON (instrument_token) " +
            "    instrument_token, tradingsymbol, name, exchange, instrument_type, " +
            "    trade_date, close_price, volume, avg_volume_30d, week52_high, week52_low, " +
            "    pct_1d, pct_3d, pct_7d, pct_10d, pct_15d, pct_1m " +
            "  FROM v_stock_analysis " +
            "  WHERE exchange = 'BSE' " +
            "  ORDER BY instrument_token, trade_date DESC " +
            ") ";

        // Unpivot all pct windows into separate rows
        String dataSql = cte +
            "SELECT instrument_token, tradingsymbol, name, exchange, instrument_type, " +
            "  trade_date, close_price, volume, avg_volume_30d, week52_high, week52_low, 'pct_1d' AS window, pct_1d AS pct_change FROM latest WHERE pct_1d IS NOT NULL AND pct_1d " + signOp + " 0 " +
            "UNION ALL SELECT instrument_token, tradingsymbol, name, exchange, instrument_type, " +
            "  trade_date, close_price, volume, avg_volume_30d, week52_high, week52_low, 'pct_3d' AS window, pct_3d AS pct_change FROM latest WHERE pct_3d IS NOT NULL AND pct_3d " + signOp + " 0 " +
            "UNION ALL SELECT instrument_token, tradingsymbol, name, exchange, instrument_type, " +
            "  trade_date, close_price, volume, avg_volume_30d, week52_high, week52_low, 'pct_7d' AS window, pct_7d AS pct_change FROM latest WHERE pct_7d IS NOT NULL AND pct_7d " + signOp + " 0 " +
            "UNION ALL SELECT instrument_token, tradingsymbol, name, exchange, instrument_type, " +
            "  trade_date, close_price, volume, avg_volume_30d, week52_high, week52_low, 'pct_10d' AS window, pct_10d AS pct_change FROM latest WHERE pct_10d IS NOT NULL AND pct_10d " + signOp + " 0 " +
            "UNION ALL SELECT instrument_token, tradingsymbol, name, exchange, instrument_type, " +
            "  trade_date, close_price, volume, avg_volume_30d, week52_high, week52_low, 'pct_15d' AS window, pct_15d AS pct_change FROM latest WHERE pct_15d IS NOT NULL AND pct_15d " + signOp + " 0 " +
            "UNION ALL SELECT instrument_token, tradingsymbol, name, exchange, instrument_type, " +
            "  trade_date, close_price, volume, avg_volume_30d, week52_high, week52_low, 'pct_1m' AS window, pct_1m AS pct_change FROM latest WHERE pct_1m IS NOT NULL AND pct_1m " + signOp + " 0 " +
            "ORDER BY pct_change " + sortDir + " " +
            "LIMIT ? OFFSET ?";

        // Count total rows
        String countSql = cte +
            "SELECT COUNT(*) FROM (" +
            "SELECT 1 FROM latest WHERE pct_1d IS NOT NULL AND pct_1d " + signOp + " 0 " +
            "UNION ALL SELECT 1 FROM latest WHERE pct_3d IS NOT NULL AND pct_3d " + signOp + " 0 " +
            "UNION ALL SELECT 1 FROM latest WHERE pct_7d IS NOT NULL AND pct_7d " + signOp + " 0 " +
            "UNION ALL SELECT 1 FROM latest WHERE pct_10d IS NOT NULL AND pct_10d " + signOp + " 0 " +
            "UNION ALL SELECT 1 FROM latest WHERE pct_15d IS NOT NULL AND pct_15d " + signOp + " 0 " +
            "UNION ALL SELECT 1 FROM latest WHERE pct_1m IS NOT NULL AND pct_1m " + signOp + " 0 " +
            ") AS all_rows";

        log.info("Query: trend={}, page={}, size={}", trend, page, size);

        try {
            // Get total count
            Integer total = jdbcTemplate.queryForObject(countSql, Integer.class);
            if (total == null) total = 0;

            // Get paginated data
            List<Map<String, Object>> data = jdbcTemplate.queryForList(dataSql, size, page * size);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("page", page);
            response.put("size", size);
            response.put("total", total);
            response.put("has_next", (page + 1) * size < total);
            response.put("data", data);

            log.info("Returned {} rows", data.size());
            return response;

        } catch (Exception e) {
            log.error("Error querying stocks: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }
}