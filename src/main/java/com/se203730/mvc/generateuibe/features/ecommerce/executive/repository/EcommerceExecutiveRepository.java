
package com.se203730.mvc.generateuibe.features.ecommerce.executive.repository;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.executive.dto.response.EcommercePageResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

@Repository
public class EcommerceExecutiveRepository {

    private final NamedParameterJdbcTemplate named;

    public EcommerceExecutiveRepository(JdbcTemplate jdbc) {
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public int update(String sql, MapSqlParameterSource params) {
        return named.update(sql, params);
    }

    public <T> T queryForObject(String sql, MapSqlParameterSource params, Class<T> type) {
        return named.queryForObject(sql, params, type);
    }

    public <T> List<T> queryForList(String sql, MapSqlParameterSource params, Class<T> type) {
        return named.queryForList(sql, params, type);
    }

    public BigDecimal lockListPrice(UUID productId) {
        return named.query(
            "select list_price from public.product_prices where product_id=:id for update",
            new MapSqlParameterSource("id", productId),
            rs -> rs.next() ? rs.getBigDecimal(1) : null
        );
    }

    // Convert SQL snake_case column labels into camelCase.
    public static Map<String, Object> asMap(
        ResultSet rs,
        int ignored
    ) throws SQLException {

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        ResultSetMetaData meta = rs.getMetaData();

        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String column = meta.getColumnLabel(i);

            StringBuilder key = new StringBuilder();
            boolean upper = false;

            for (char ch : column.toCharArray()) {
                if (ch == '_') {
                    upper = true;
                    continue;
                }

                key.append(
                    upper ? Character.toUpperCase(ch) : ch
                );
                upper = false;
            }

            result.put(key.toString(), rs.getObject(i));
        }

        return result;
    }

    public List<Map<String, Object>> list(
        String sql,
        MapSqlParameterSource params
    ) {
        return named.query(
            sql,
            params,
            EcommerceExecutiveRepository::asMap
        );
    }

    public Map<String, Object> one(
        String sql,
        MapSqlParameterSource params
    ) {
        var result = list(sql, params);

        if (result.isEmpty()) {
            throw new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        return result.getFirst();
    }

    public EcommercePageResponse<Map<String, Object>> page(
        String sql,
        String countSql,
        MapSqlParameterSource params,
        int page,
        int size
    ) {
        if (page < 0 || size < 1 || size > 100 || page > 100000) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST
            );
        }

        Long total = named.queryForObject(
            countSql,
            params,
            Long.class
        );

        params.addValue("limitRows", size)
            .addValue("offsetRows", page * (long) size);

        var items = list(
            sql + " limit :limitRows offset :offsetRows",
            params
        );

        return new EcommercePageResponse<>(
            items,
            total == null ? 0 : total,
            page,
            size
        );
    }

    public static String find(String input, int maxLength) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.strip();

        if (normalized.length() > maxLength) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST
            );
        }

        return normalized
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}
