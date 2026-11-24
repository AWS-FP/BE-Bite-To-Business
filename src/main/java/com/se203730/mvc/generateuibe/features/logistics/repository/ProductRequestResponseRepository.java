package com.se203730.mvc.generateuibe.features.logistics.repository;

import com.se203730.mvc.generateuibe.features.logistics.entity.ProductRequestResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ProductRequestResponseRepository extends JpaRepository<ProductRequestResponse, UUID> {
    List<ProductRequestResponse> findByRequestIdInOrderByCreatedAtDesc(java.util.Collection<UUID> requestIds);

    interface ProductSnapshot {
        String getName();
        String getUnit();
        Instant getAddedAt();
        BigDecimal getPrice();
        BigDecimal getAvailable();
    }

    @Query(value = """
            select p.name_vi as name, p.unit_code as unit, p.created_at as "addedAt",
                   prices.list_price as price, inventory.available_quantity as available
            from public.products p
            join public.product_prices prices on prices.product_id = p.id
            join public.inventory inventory on inventory.product_id = p.id
            where p.id = :id and p.status = 'PUBLISHED'
            """, nativeQuery = true)
    Optional<ProductSnapshot> findProductSnapshot(@Param("id") UUID id);

    @Query(value = """
            select * from public.product_request_responses
            where email_status = 'PENDING' and next_email_attempt_at <= now()
                and (email_claimed_until is null or email_claimed_until <= now())
            order by next_email_attempt_at, created_at limit 1 for update skip locked
            """, nativeQuery = true)
    Optional<ProductRequestResponse> findNextEmail();

    @Modifying
    @Query(value = """
            update public.product_request_responses
            set email_status = 'SENT', notified_at = now(), next_email_attempt_at = null,
                email_claim_token = null, email_claimed_until = null, updated_at = now()
            where id = :id and email_status = 'PENDING' and email_claim_token = :token
            """, nativeQuery = true)
    int markEmailSent(@Param("id") UUID id, @Param("token") UUID token);

    @Modifying
    @Query(value = """
            update public.product_request_responses
            set email_attempts = email_attempts + 1,
                next_email_attempt_at = now() + (:delaySeconds * interval '1 second'),
                email_claim_token = null, email_claimed_until = null, updated_at = now()
            where id = :id and email_status = 'PENDING' and email_claim_token = :token
            """, nativeQuery = true)
    int retryEmail(@Param("id") UUID id, @Param("token") UUID token, @Param("delaySeconds") long delaySeconds);
}
