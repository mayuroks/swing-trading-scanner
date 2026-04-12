package com.example.demo;

import org.springframework.stereotype.Component;

@Component
public class TokenStore {

    private volatile String authHeader = "";

    public String getAuthHeader() {
        return authHeader;
    }

    public void setAuthHeader(String token) {
        this.authHeader = "token " + token;
    }
}
