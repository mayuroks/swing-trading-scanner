package com.example.demo;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final TokenStore tokenStore;
    private final TokenService tokenService;

    public AdminController(TokenStore tokenStore, TokenService tokenService) {
        this.tokenStore = tokenStore;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public String login(@RequestBody Map<String, String> body) {
        String requestToken = body.get("request_token");
        if (requestToken == null || requestToken.isBlank()) {
            return "Error: 'request_token' field missing";
        }
        try {
            return tokenService.generateAccessToken(requestToken);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @PostMapping("/token")
    public String updateToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return "Error: 'token' field missing";
        }
        tokenStore.setAuthHeader(token);
        return "Token updated";
    }
}
