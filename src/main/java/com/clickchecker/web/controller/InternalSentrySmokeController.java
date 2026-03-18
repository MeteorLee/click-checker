package com.clickchecker.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalSentrySmokeController {

    @PostMapping("/internal/sentry/smoke")
    public void trigger() {
        throw new IllegalStateException("sentry smoke");
    }
}
