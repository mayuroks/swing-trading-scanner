package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.chat.id:}")
    private String chatId;

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    public TelegramService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void sendDropReport() {
        if (botToken == null || botToken.isEmpty() || chatId == null || chatId.isEmpty()) {
            log.warn("Telegram config missing. Skipping drop report.");
            return;
        }

        try {
            String sql = "WITH latest AS (" +
                    "  SELECT DISTINCT ON (instrument_token) " +
                    "    instrument_token, tradingsymbol, name, close_price, volume, avg_volume_30d, week52_low, " +
                    "    pct_1d, pct_3d, pct_7d, pct_10d, pct_15d, pct_1m " +
                    "  FROM v_stock_analysis " +
                    "  WHERE exchange = 'BSE' AND volume >= 50000 " +
                    "  ORDER BY instrument_token, trade_date DESC " +
                    ") " +
                    "SELECT tradingsymbol, name, close_price, volume, avg_volume_30d, week52_low, 'pct_1d' AS time_window, pct_1d AS pct_change FROM latest WHERE pct_1d IS NOT NULL AND pct_1d <= -10 " +
                    "UNION ALL SELECT tradingsymbol, name, close_price, volume, avg_volume_30d, week52_low, 'pct_3d', pct_3d FROM latest WHERE pct_3d IS NOT NULL AND pct_3d <= -10 " +
                    "UNION ALL SELECT tradingsymbol, name, close_price, volume, avg_volume_30d, week52_low, 'pct_7d', pct_7d FROM latest WHERE pct_7d IS NOT NULL AND pct_7d <= -10 " +
                    "UNION ALL SELECT tradingsymbol, name, close_price, volume, avg_volume_30d, week52_low, 'pct_10d', pct_10d FROM latest WHERE pct_10d IS NOT NULL AND pct_10d <= -10 " +
                    "UNION ALL SELECT tradingsymbol, name, close_price, volume, avg_volume_30d, week52_low, 'pct_15d', pct_15d FROM latest WHERE pct_15d IS NOT NULL AND pct_15d <= -10 " +
                    "UNION ALL SELECT tradingsymbol, name, close_price, volume, avg_volume_30d, week52_low, 'pct_1m', pct_1m FROM latest WHERE pct_1m IS NOT NULL AND pct_1m <= -10 " +
                    "ORDER BY time_window, pct_change ASC";

            List<Map<String, Object>> drops = jdbcTemplate.queryForList(sql);

            if (drops.isEmpty()) {
                sendTelegramMessage("✅ No major drops today.");
            } else {
                String fullMessage = buildDropMessage(drops);
                List<String> messages = splitMessages(fullMessage);
                sendMessages(messages);
            }
        } catch (Exception e) {
            log.error("Error sending drop report: {}", e.getMessage(), e);
        }
    }

    private String formatVolume(long volume) {
        if (volume >= 1_000_000) {
            return String.format("%.1fM", volume / 1_000_000.0);
        } else if (volume >= 1_000) {
            return String.format("%.0fK", volume / 1_000.0);
        }
        return String.valueOf(volume);
    }

    private void sendMessages(List<String> messages) {
        for (int i = 0; i < messages.size(); i++) {
            sendTelegramMessage(messages.get(i));
            if (i < messages.size() - 1) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private List<String> splitMessages(String fullMessage) {
        List<String> messages = new ArrayList<>();
        int maxLength = 3500;

        if (fullMessage.length() <= maxLength) {
            messages.add(fullMessage);
            return messages;
        }

        String[] lines = fullMessage.split("\n");
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            String lineWithNewline = line + "\n";

            if (current.length() + lineWithNewline.length() > maxLength) {
                if (current.length() > 0) {
                    messages.add(current.toString().trim());
                    current = new StringBuilder();
                }
            }
            current.append(lineWithNewline);
        }

        if (current.length() > 0) {
            messages.add(current.toString().trim());
        }

        return messages;
    }

    private String buildDropMessage(List<Map<String, Object>> drops) {
        StringBuilder sb = new StringBuilder();
        sb.append("📉 <b>Drop Alert — ").append(LocalDate.now()).append("</b>\n\n");

        // Group by window, preserving order
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        String[] windowOrder = {"pct_1d", "pct_3d", "pct_7d", "pct_10d", "pct_15d", "pct_1m"};
        for (String window : windowOrder) {
            grouped.put(window, new ArrayList<>());
        }

        for (Map<String, Object> drop : drops) {
            String window = (String) drop.get("time_window");
            grouped.get(window).add(drop);
        }

        // Build message by window (all drops, will be split across messages if needed)
        for (String window : windowOrder) {
            List<Map<String, Object>> windowDrops = grouped.get(window);
            if (!windowDrops.isEmpty()) {
                String windowLabel = getWindowLabel(window);
                sb.append("<b>").append(windowLabel).append("</b>\n");

                for (Map<String, Object> drop : windowDrops) {
                    String symbol = (String) drop.get("tradingsymbol");
                    double pctChange = ((Number) drop.get("pct_change")).doubleValue();
                    double closePrice = ((Number) drop.get("close_price")).doubleValue();
                    long volume = ((Number) drop.get("volume")).longValue();
                    double avgVolume = ((Number) drop.get("avg_volume_30d")).doubleValue();
                    double week52Low = ((Number) drop.get("week52_low")).doubleValue();

                    double volRatio = avgVolume > 0 ? volume / avgVolume : 0;
                    double pctFromLow = week52Low > 0 ? ((closePrice - week52Low) / week52Low) * 100 : 0;

                    sb.append("• ").append(symbol).append(" — ").append(String.format("%.1f%%", pctChange))
                        .append(" | ₹").append(String.format("%.2f", closePrice))
                        .append(" | Vol ").append(formatVolume(volume)).append(" (").append(String.format("%.1f", volRatio)).append("x avg)")
                        .append(" | +").append(String.format("%.0f%%", pctFromLow)).append(" from low\n");
                }
                sb.append("\n");
            }
        }

        // Add guidance (at end, will be in last message if split)
        sb.append("<b>📊 Guidance:</b>\n");
        sb.append("• High vol (&gt;1.5x avg) + near low (&lt;20% from low) → avoid (knife falling)\n");
        sb.append("• High vol + far from low (&gt;50% from low) → potential buy (healthy pullback)\n");
        sb.append("• Low vol drop (&lt;1x avg) → thin selling, wait for confirmation\n");

        return sb.toString();
    }

    private String getWindowLabel(String window) {
        return switch (window) {
            case "pct_1d" -> "1D Drops (≥10%)";
            case "pct_3d" -> "3D Drops (≥10%)";
            case "pct_7d" -> "7D Drops (≥10%)";
            case "pct_10d" -> "10D Drops (≥10%)";
            case "pct_15d" -> "15D Drops (≥10%)";
            case "pct_1m" -> "1M Drops (≥10%)";
            default -> window;
        };
    }

    public void sendMessage(String text) {
        sendTelegramMessage(text);
    }

    private void sendTelegramMessage(String text) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String body = "chat_id=" + chatId + "&text=" + encodedText + "&parse_mode=HTML";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            restTemplate.postForObject(url, entity, String.class);
        } catch (Exception e) {
            log.error("Failed to send Telegram message: {}", e.getMessage(), e);
        }
    }
}
