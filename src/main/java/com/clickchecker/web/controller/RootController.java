package com.clickchecker.web.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, String> root() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("service", "click-checker");
        response.put("status", "ok");
        response.put("health", "/actuator/health");
        return response;
    }
}
