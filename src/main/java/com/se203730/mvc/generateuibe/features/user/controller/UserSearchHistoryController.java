package com.se203730.mvc.generateuibe.features.user.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.user.dto.request.SaveSearchHistoryRequest;
import com.se203730.mvc.generateuibe.features.user.dto.response.SearchHistoryResponse;
import com.se203730.mvc.generateuibe.features.user.service.UserSearchHistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me/search-history")
@Tag(name = "User - Search history")
public class UserSearchHistoryController {
    @Autowired
    private UserSearchHistoryService historyService;

    @GetMapping
    public ResponseEntity<ResponseObject<List<SearchHistoryResponse>>> getRecentHistory(
            @CurrentUserUUID UUID userId) {
        List<SearchHistoryResponse> data = historyService.getRecentHistory(userId);
        ResponseObject<List<SearchHistoryResponse>> response = new ResponseObject<>(200, "Success", data);

        // Không để trình duyệt/proxy cache lịch sử cá nhân rồi hiển thị cho tài khoản khác.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping
    public ResponseEntity<ResponseObject<List<SearchHistoryResponse>>> saveSearch(
            @CurrentUserUUID UUID userId,
            @Valid @RequestBody SaveSearchHistoryRequest request) {
        List<SearchHistoryResponse> data = historyService.saveSearch(userId, request);
        ResponseObject<List<SearchHistoryResponse>> response = new ResponseObject<>(200, "Search saved.", data);

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
