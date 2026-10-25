package com.se203730.mvc.generateuibe.features.user.repository;

import com.se203730.mvc.generateuibe.features.user.entity.UserSearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserSearchHistoryRepository extends JpaRepository<UserSearchHistory, UUID> {
    // Spring Data sinh query lọc theo user, sắp xếp mới nhất trước và giới hạn 5 dòng.
    List<UserSearchHistory> findTop5ByUserIdOrderBySearchedAtDescIdDesc(UUID userId);

    // INSERT ... ON CONFLICT tránh tạo dòng trùng khi hai tab cùng gửi một từ khóa.
    @Modifying
    @Query(value = """
            INSERT INTO public.user_search_history (id, user_id, keyword, searched_at)
            VALUES (:id, :userId, :keyword, statement_timestamp())
            ON CONFLICT (user_id, LOWER(keyword))
            DO UPDATE SET keyword = EXCLUDED.keyword, searched_at = EXCLUDED.searched_at
            """, nativeQuery = true)
    void saveKeyword(@Param("id") UUID id,
                     @Param("userId") UUID userId,
                     @Param("keyword") String keyword);
}
