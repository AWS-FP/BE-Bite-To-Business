package com.se203730.mvc.generateuibe.features.ai.repository;

import com.se203730.mvc.generateuibe.features.ai.entity.AiModelRequest;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Ghi log từng lần gọi model (bước, token, thời gian, mã lỗi) vào ai_model_requests để đo chi phí/độ trễ. */
public interface AiModelRequestRepository extends JpaRepository<AiModelRequest, UUID> {
    @Query(value = """
            SELECT floor(extract(epoch FROM created_at) / :period) * :period AS bucket,
                   count(*), count(*) FILTER (WHERE status = 'FAILED'),
                   coalesce(sum(input_tokens), 0), coalesce(sum(output_tokens), 0)
            FROM public.ai_model_requests
            WHERE created_at >= :from AND created_at < :to
            GROUP BY bucket ORDER BY bucket
            """, nativeQuery = true)
    List<Object[]> summarize(@Param("from") Instant from, @Param("to") Instant to,
                            @Param("period") int period);

    List<AiModelRequest> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Instant from, Instant to, Pageable pageable);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO public.ai_model_requests (model_id, status, input_tokens, output_tokens, duration_ms, error_code)
            VALUES (:modelId, :status, :inputTokens, :outputTokens, :durationMs, :errorCode)
            """, nativeQuery = true)
    void record(
            @Param("modelId") String modelId,
            @Param("status") String status,
            @Param("inputTokens") Long inputTokens,
            @Param("outputTokens") Long outputTokens,
            @Param("durationMs") Long durationMs,
            @Param("errorCode") String errorCode);
}
