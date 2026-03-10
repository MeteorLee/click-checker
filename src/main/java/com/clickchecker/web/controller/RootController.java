package com.clickchecker.web.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    private final String appColor = Optional.ofNullable(System.getenv("APP_COLOR")).orElse("default");

    @GetMapping("/")
    public Map<String, String> root() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("service", "click-checker");
        response.put("status", "ok");
        response.put("health", "/actuator/health");
        response.put("color", appColor);
        return response;
    }
}
