package com.se203730.mvc.generateuibe.features.ai.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.features.ai.dto.request.UiEventBatchRequest;
import com.se203730.mvc.generateuibe.features.ai.service.UiEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui-events")
@Tag(name = "AI")
public class UiEventController {
    private final UiEventService events;

    public UiEventController(UiEventService events) {
        this.events = events;
    }

    @PostMapping
    @Operation(summary = "Record storefront and A2UI interaction events used to personalise generated UI")
    public ResponseEntity<Void> record(
            @CurrentUserUUID(required = false)
            UUID userId,
            @Valid
            @RequestBody
            UiEventBatchRequest request) {
        events.record(userId, request);
        return ResponseEntity.noContent().build();
    }
}
