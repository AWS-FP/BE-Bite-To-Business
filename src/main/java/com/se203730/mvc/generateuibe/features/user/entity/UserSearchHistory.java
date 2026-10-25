package com.se203730.mvc.generateuibe.features.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Một từ khóa trong lịch sử tìm kiếm của một tài khoản. */
@Entity
@Table(name = "user_search_history", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class UserSearchHistory {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "keyword", nullable = false, length = 160)
    private String keyword;

    @Column(name = "searched_at", nullable = false)
    private Instant searchedAt;
}
