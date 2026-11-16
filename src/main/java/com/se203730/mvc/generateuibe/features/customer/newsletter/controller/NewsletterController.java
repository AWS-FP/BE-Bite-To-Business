package com.se203730.mvc.generateuibe.features.customer.newsletter.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.newsletter.dto.request.NewsletterSubscribeRequest;
import com.se203730.mvc.generateuibe.features.customer.newsletter.dto.response.NewsletterSubscriptionResponse;
import com.se203730.mvc.generateuibe.features.customer.newsletter.service.NewsletterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/newsletter/subscriptions")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Homepage - Newsletter")
public class NewsletterController {

    private final NewsletterService service;

    @PostMapping
    @Operation(
        summary = "Subscribe to newsletter",
        description = "Register an email address to receive newsletters, "
            + "product updates and promotional offers. "
            + "Requires explicit consent."
    )
    public ResponseEntity<ResponseObject<NewsletterSubscriptionResponse>> subscribe(
        @Valid @RequestBody NewsletterSubscribeRequest request) {

        NewsletterSubscriptionResponse response = service.subscribe(request);

        return ResponseEntity.ok(
            new ResponseObject<>(
                200,
                "Success",
                response
            )
        );
    }
}
