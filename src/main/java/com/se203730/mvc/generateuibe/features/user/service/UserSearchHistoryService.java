package com.se203730.mvc.generateuibe.features.user.service;

import com.se203730.mvc.generateuibe.features.auth.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.user.dto.request.SaveSearchHistoryRequest;
import com.se203730.mvc.generateuibe.features.user.dto.response.SearchHistoryResponse;
import com.se203730.mvc.generateuibe.features.user.entity.UserSearchHistory;
import com.se203730.mvc.generateuibe.features.user.repository.UserSearchHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Controller -> Service kiểm tra nghiệp vụ -> Repository -> JPA/JDBC -> Database. */
@Service
public class UserSearchHistoryService {
    @Autowired
    private UserSearchHistoryRepository historyRepository;

    @Autowired
    private AccountAccessService accountAccessService;

    @Transactional(readOnly = true)
    public List<SearchHistoryResponse> getRecentHistory(UUID userId) {
        accountAccessService.requireActiveUser(userId);
        return readRecentHistory(userId);
    }

    @Transactional
    public List<SearchHistoryResponse> saveSearch(UUID userId, SaveSearchHistoryRequest request) {
        accountAccessService.requireActiveUser(userId);

        // DB cập nhật thời gian nếu từ khóa đã có; không thay lịch sử của user khác.
        historyRepository.saveKeyword(UUID.randomUUID(), userId, request.keyword());
        return readRecentHistory(userId);
    }

    private List<SearchHistoryResponse> readRecentHistory(UUID userId) {
        List<UserSearchHistory> historyList = historyRepository.findTop5ByUserIdOrderBySearchedAtDescIdDesc(userId);
        List<SearchHistoryResponse> responses = new ArrayList<>();

        for (UserSearchHistory history : historyList) {
            responses.add(SearchHistoryResponse.from(history));
        }

        return responses;
    }
}
