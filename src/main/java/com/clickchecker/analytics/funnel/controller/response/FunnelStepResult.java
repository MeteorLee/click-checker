package com.clickchecker.analytics.funnel.controller.response;

public record FunnelStepResult(
        int stepOrder,
        String canonicalEventType,
        long users,
        Double conversionRateFromFirstStep,
        Long previousStepUsers,
        Double conversionRateFromPreviousStep,
        Long dropOffUsersFromPreviousStep
) {
}
