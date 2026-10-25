package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.admin.repository.RegistrationRequestRepository;
import com.se203730.mvc.generateuibe.features.user.entity.User;
import com.se203730.mvc.generateuibe.features.user.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.user.enums.CustomerType;
import com.se203730.mvc.generateuibe.features.user.enums.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Chỉ dùng database H2 trong bộ nhớ; không chạy Flyway hoặc kết nối Supabase của nhóm.
@ActiveProfiles("test")
@DataJpaTest(properties = {"spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "logging.level.root=WARN", "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate.SQL=OFF"}, showSql = false)
class RegistrationFiltersTest {
    @Autowired
    EntityManager entityManager;

    @Autowired
    RegistrationRequestRepository registrations;

    private UUID newest;

    @BeforeEach
    void prepare() {
        create("An", "Nguyễn", "an_under@gmail.com", "+84900000001", RegistrationStatus.PENDING,
                CustomerType.HOUSEHOLD_INDIVIDUAL_BUSINESS, 1);
        create("Bình", "Trần", "binh@gmail.com", "+84900000002", RegistrationStatus.APPROVED,
                CustomerType.BUSINESS_COOPERATIVE, 2);
        newest = create("Chi", "Nguyễn", "chi@gmail.com", "+84900000003",
                RegistrationStatus.PENDING, CustomerType.BUSINESS_COOPERATIVE, 3);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void includesAllStatusesAndPaginatesAfterFiltering() {
        var page = registrations.findAll(RegistrationFilters.matching(null, null, null),
                PageRequest.of(1, 1, Sort.by("submittedAt").descending()));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(RegistrationRequest::getStatus)
                .containsExactly(RegistrationStatus.APPROVED);
        assertThat(registrations.countByStatus(RegistrationStatus.PENDING)).isEqualTo(2);
    }

    @Test
    void combinesStatusTypeAndCaseInsensitiveFullNameBeforePagination() {
        var page = registrations.findAll(RegistrationFilters.matching(RegistrationStatus.PENDING,
                "  NGUYỄN  ", CustomerType.BUSINESS_COOPERATIVE), PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(RegistrationRequest::getId)
                .containsExactly(newest);
    }

    @Test
    void matchesFullNameInBothOrdersAndNormalizedPhone() {
        assertThat(registrations.findAll(RegistrationFilters.matching(null, "Nguyễn Chi", null)))
                .hasSize(1);
        assertThat(registrations.findAll(RegistrationFilters.matching(null, "Chi Nguyễn", null)))
                .hasSize(1);
        assertThat(registrations.findAll(RegistrationFilters.matching(null, "0900 000 003", null)))
                .extracting(RegistrationRequest::getId).containsExactly(newest);
        assertThat(
                registrations.findAll(RegistrationFilters.matching(null, "BINH@GMAIL.COM", null)))
                .hasSize(1);
    }

    @Test
    void treatsLikeWildcardsAsLiteralSearchText() {
        assertThat(registrations.findAll(RegistrationFilters.matching(null, "_", null))).hasSize(1);
        assertThat(registrations.findAll(RegistrationFilters.matching(null, "%", null))).isEmpty();
        assertThat(registrations.findAll(RegistrationFilters.matching(null, "!", null))).isEmpty();
    }

    private UUID create(
            String firstName,
            String lastName,
            String email,
            String phone,
            RegistrationStatus status,
            CustomerType type,
            long sequence) {
        UUID id = UUID.randomUUID();
        Instant time = Instant.parse("2026-10-25T00:00:00Z").plusSeconds(sequence);
        User user = new User();
        user.setId(id);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(UserRole.CUSTOMER);
        user.setCreatedAt(time);
        user.setUpdatedAt(time);
        entityManager.persist(user);
        UserProfile profile = new UserProfile();
        profile.setUserId(id);
        profile.setCustomerType(type);
        profile.setCreatedAt(time);
        profile.setUpdatedAt(time);
        entityManager.persist(profile);
        RegistrationRequest registration = new RegistrationRequest();
        registration.setId(id);
        registration.setCustomerId(id);
        registration.setStatus(status);
        registration.setSubmittedAt(time);
        registration.setCreatedAt(time);
        registration.setUpdatedAt(time);
        entityManager.persist(registration);
        return id;
    }
}
