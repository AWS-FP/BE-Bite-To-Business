package com.se203730.mvc.generateuibe.features.ai.repository;

import com.se203730.mvc.generateuibe.features.ai.entity.AiRequestUsage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiRequestUsageRepository extends JpaRepository<AiRequestUsage, UUID> {
    @Query(value = "SELECT id FROM public.users WHERE id = :userId FOR UPDATE", nativeQuery = true)
    UUID lockUser(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM public.ai_request_usage WHERE user_id = :userId AND requested_at <= statement_timestamp() - interval '1 hour'", nativeQuery = true)
    void deleteExpired(@Param("userId") UUID userId);

    @Query(value = "SELECT count(*) FROM public.ai_request_usage WHERE user_id = :userId AND requested_at > statement_timestamp() - interval '1 hour'", nativeQuery = true)
    long countRecent(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "INSERT INTO public.ai_request_usage (id, user_id) VALUES (:id, :userId)", nativeQuery = true)
    void recordRequest(@Param("id") UUID id, @Param("userId") UUID userId);
}
