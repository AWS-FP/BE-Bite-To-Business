package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.Quote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {
    Optional<Quote> findFirstByCustomerIdAndStatusOrderByUpdatedAtDesc(UUID customerId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quote q where q.id = :id and q.customerId = :customerId")
    Optional<Quote> findOwnedForUpdate(@Param("id") UUID id, @Param("customerId") UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quote q where q.id = :id")
    Optional<Quote> findForUpdate(@Param("id") UUID id);

    Page<Quote> findByStatusOrderBySubmittedAtAsc(String status, Pageable page);

    Page<Quote> findByCustomerIdOrderByUpdatedAtDesc(UUID customerId, Pageable page);

    @Modifying
    @Query(value = """
        insert into public.quote_revisions (quote_id, revision, snapshot, change_note, created_by, created_at)
        select q.id, q.revision,
            jsonb_build_object(
                'quote', to_jsonb(q),
                'items', coalesce((select jsonb_agg(to_jsonb(i) order by i.created_at, i.id)
                    from public.quote_items i where i.quote_id = q.id), cast('[]' as jsonb)),
                'packages', coalesce((select jsonb_agg(to_jsonb(p) order by p.created_at, p.id)
                    from public.quote_packages p where p.quote_id = q.id and p.cancelled_at is null), cast('[]' as jsonb)),
                'allocations', coalesce((select jsonb_agg(to_jsonb(a) order by a.created_at, a.id)
                    from public.quote_package_items a join public.quote_packages p on p.id = a.quote_package_id
                    where a.quote_id = q.id and p.cancelled_at is null), cast('[]' as jsonb))
            ), :note, :actor, :createdAt
        from public.quotes q where q.id = :id
        on conflict (quote_id, revision) do nothing
        """, nativeQuery = true)
    int saveRevision(@Param("id") UUID id, @Param("actor") UUID actor,
                     @Param("note") String note, @Param("createdAt") java.time.Instant createdAt);
}
