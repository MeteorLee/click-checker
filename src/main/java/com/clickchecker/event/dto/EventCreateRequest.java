package com.clickchecker.event.dto;

import com.clickchecker.event.entity.ActionType;
import com.clickchecker.event.entity.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor
@Getter
public class EventCreateRequest {

    @NotBlank
    private String serviceId;

    @NotNull
    private TargetType targetType;

    @NotBlank
    private String targetId;

    @NotNull
    private ActionType actionType;

    @NotNull
    private Instant occurredAt;

}
