package com.se203730.mvc.generateuibe.common.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
public class DatabaseClock {
    @Autowired
    private JdbcTemplate jdbc;

    public Instant now() {
        // Lấy giờ thực sau khi khóa hồ sơ, không dùng giờ bắt đầu transaction hoặc đồng hồ máy local.
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT clock_timestamp()", (row, index) -> row.getTimestamp(1).toInstant()));
    }
}
