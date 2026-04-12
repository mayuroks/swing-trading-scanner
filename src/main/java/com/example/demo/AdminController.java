package com.example.demo;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final TokenStore tokenStore;

    public AdminController(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
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
