package com.se203730.mvc.generateuibe.features.ai.repository;

import com.se203730.mvc.generateuibe.features.ai.entity.UiInteractionEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UiInteractionEventRepository extends JpaRepository<UiInteractionEvent, UUID> {
    /** Gửi lại cùng eventId thì bỏ qua, không ghi trùng. */
    @Modifying
    @Query(value = """
            INSERT INTO public.ui_interaction_events
                (id, user_id, anon_id, event_type, route, surface_id, component, target, value, occurred_at)
            VALUES (:id, :userId, :anonId, :eventType, :route, :surfaceId, :component, :target,
                    CAST(:value AS JSONB), :occurredAt)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnoringDuplicate(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("anonId") String anonId,
            @Param("eventType") String eventType,
            @Param("route") String route,
            @Param("surfaceId") String surfaceId,
            @Param("component") String component,
            @Param("target") String target,
            @Param("value") String value,
            @Param("occurredAt") Instant occurredAt);

    @Modifying
    @Query(value = "DELETE FROM public.ui_interaction_events WHERE received_at < :cutoff", nativeQuery = true)
    int deleteReceivedBefore(@Param("cutoff") Instant cutoff);
}
