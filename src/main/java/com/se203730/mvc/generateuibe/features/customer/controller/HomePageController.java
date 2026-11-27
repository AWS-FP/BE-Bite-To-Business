package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.dto.response.HomePageBannerResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.HomePageCategoryResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.HomePageResponse;
import com.se203730.mvc.generateuibe.features.customer.service.HomePageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import com.se203730.mvc.generateuibe.features.customer.dto.response.HomePageComboResponse;
import org.springframework.web.bind.annotation.RequestParam;
import com.se203730.mvc.generateuibe.features.customer.dto.response.HomePageComboPageResponse;

@RestController
@RequestMapping("/api/homepage")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name="Public - Homepage (Customer module)")
public class HomePageController {
 private final HomePageService service;
 @GetMapping
 @Operation(summary="Homepage cards: standard products, flash sales, combos, promotions and banners")
 public ResponseEntity<ResponseObject<HomePageResponse>> home() {
  return ResponseEntity.ok(new ResponseObject<>(200,"Success",service.home()));
 }
 @GetMapping("/combos")
 @Operation(summary="Published combos with bounded pagination")
 public ResponseEntity<ResponseObject<HomePageComboPageResponse>> combos(
     @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size) {
  return ResponseEntity.ok(new ResponseObject<>(200,"Success",service.comboPage(page,size)));
 }
 @GetMapping("/combos/{id}")
 @Operation(summary="Published combo with its ingredients")
 public ResponseEntity<ResponseObject<HomePageComboResponse>> combo(@PathVariable UUID id) {
  return ResponseEntity.ok(new ResponseObject<>(200,"Success",service.combo(id)));
 }
 @GetMapping("/categories")
 @Operation(summary="Active parent/child categories with published productCount including descendants")
 public ResponseEntity<ResponseObject<List<HomePageCategoryResponse>>> categories() {
  return ResponseEntity.ok(new ResponseObject<>(200,"Success",service.categories()));
 }
 @GetMapping("/banners")
 @Operation(summary="Active homepage banners, sorted")
 public ResponseEntity<ResponseObject<List<HomePageBannerResponse>>> banners() {
  return ResponseEntity.ok(new ResponseObject<>(200,"Success",service.banners()));
 }
}
