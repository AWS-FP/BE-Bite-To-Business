package com.se203730.mvc.generateuibe.features.ecommerce.home.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ecommerce.home.dto.response.HomeResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.home.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/home")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Catalog - Home")
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    @Operation(summary = "Home: featured products, flash sales, combos and current promotions")
    public ResponseEntity<ResponseObject<HomeResponse>> getHome() {
        return ResponseEntity.ok(
                new ResponseObject<>(200, "Success", homeService.getHome())
        );
    }
}
