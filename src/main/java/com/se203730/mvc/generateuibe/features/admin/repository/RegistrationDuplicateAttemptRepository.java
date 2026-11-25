package com.se203730.mvc.generateuibe.features.admin.repository;

import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationDuplicateAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationDuplicateAttemptRepository extends JpaRepository<RegistrationDuplicateAttempt, UUID> {

    Page<RegistrationDuplicateAttempt> findAllByOrderByCreatedAtDescIdDesc(Pageable page);

    // Đã có mail từ chối (đã gửi hoặc đang chờ) cho địa chỉ này gần đây thì không gửi thêm (chống spam mail).
    @Query(value = """
            select exists (
                select 1 from public.registration_duplicate_attempts
                where lower(email) = lower(:email) and email_status in ('PENDING', 'SENT')
                  and created_at > :since
            )
            """, nativeQuery = true)
    boolean hasRecentRejectionMail(@Param("email") String email, @Param("since") Instant since);

    // Khách hàng có email bị đăng ký lại (trùng email): admin không được duyệt hồ sơ của họ nữa,
    // nếu không mail từ chối tự động và mail OTP sẽ cùng tới một hộp thư.
    @Query(value = """
            select distinct existing_user_id from public.registration_duplicate_attempts
            where existing_user_id in (:userIds) and duplicate_field in ('EMAIL', 'EMAIL_AND_PHONE')
            """, nativeQuery = true)
    List<UUID> findEmailDuplicateUserIds(@Param("userIds") Collection<UUID> userIds);

    // Khóa một bản ghi đến hạn gửi; SKIP LOCKED để nhiều instance không gửi trùng.
    @Query(value = """
            select * from public.registration_duplicate_attempts
            where email_status = 'PENDING' and next_email_at <= :now
            order by next_email_at, id
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<RegistrationDuplicateAttempt> lockNextDue(@Param("now") Instant now);
}
