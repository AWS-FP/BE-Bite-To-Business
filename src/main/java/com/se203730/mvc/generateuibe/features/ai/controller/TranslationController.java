package com.se203730.mvc.generateuibe.features.ai.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.ai.dto.request.TranslateRequest;
import com.se203730.mvc.generateuibe.features.ai.dto.response.TranslateResponse;
import com.se203730.mvc.generateuibe.features.ai.service.TranslationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gợi ý dịch Việt ↔ Anh cho các ô song ngữ khi nhân viên tạo/sửa sản phẩm ({@code POST /api/ai/translate}).
 * Chỉ trả gợi ý; nhân viên phải bấm "Dùng gợi ý" ở FE thì mới điền vào form.
 */
@RestController
@RequestMapping("/api/ai/translate")
@Tag(name = "AI")
public class TranslationController {
    private final TranslationService translations;

    public TranslationController(TranslationService translations) {
        this.translations = translations;
    }

    @PostMapping
    @Operation(summary = "Suggest a Vietnamese/English translation for a bilingual product field (Logistics, E-commerce)")
    public ResponseEntity<ResponseObject<TranslateResponse>> translate(
            @CurrentUserUUID
            UUID userId,
            Authentication authentication,
            @Valid
            @RequestBody
            TranslateRequest request) {
        TranslateResponse data = translations.translate(userId, authentication, request);
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", data));
    }
}
