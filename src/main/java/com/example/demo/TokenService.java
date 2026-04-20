package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final TokenStore tokenStore;
    private final KiteConfig kiteConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenService(TokenStore tokenStore, KiteConfig kiteConfig) {
        this.tokenStore = tokenStore;
        this.kiteConfig = kiteConfig;
    }

    public String generateAccessToken(String requestToken) throws Exception {
        String apiKey = kiteConfig.getApiKey();
        String apiSecret = kiteConfig.getApiSecret();

        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            throw new IllegalStateException("KITE_API_KEY and KITE_API_SECRET must be set in env vars or application-local.properties");
        }

        // Compute checksum: SHA256(api_key + request_token + api_secret)
        String checksum = computeSHA256(apiKey + requestToken + apiSecret);

        // Call Kite API to get access_token
        String url = "https://api.kite.trade/session/token";
        String body = "api_key=" + apiKey + "&request_token=" + requestToken + "&checksum=" + checksum;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("X-Kite-Version", "3");
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            String responseBody = restTemplate.postForObject(url, entity, String.class);
            JsonNode root = objectMapper.readTree(responseBody);
            String accessToken = root.path("data").path("access_token").asText();

            if (accessToken.isEmpty()) {
                throw new Exception("No access_token in Kite API response");
            }

            // Save to TokenStore as "api_key:access_token"
            String fullToken = apiKey + ":" + accessToken;
            tokenStore.setAuthHeader(fullToken);

            log.info("Access token refreshed successfully");
            return "Token updated successfully";
        } catch (Exception e) {
            log.error("Failed to get access token from Kite API: {}", e.getMessage());
            throw new Exception("Failed to get access token: " + e.getMessage(), e);
        }
    }

    private String computeSHA256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
