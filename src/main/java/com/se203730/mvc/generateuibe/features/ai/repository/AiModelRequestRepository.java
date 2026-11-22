package com.se203730.mvc.generateuibe.features.ai.repository;

import com.se203730.mvc.generateuibe.features.ai.entity.AiModelRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AiModelRequestRepository extends JpaRepository<AiModelRequest, UUID> {
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
