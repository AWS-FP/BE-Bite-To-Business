package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.common.util.PhoneNumberUtils;
import com.se203730.mvc.generateuibe.features.admin.client.SupabaseAuthClient;
import com.se203730.mvc.generateuibe.features.admin.dto.request.CreateStaffRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.response.StaffResponse;
import com.se203730.mvc.generateuibe.features.admin.enums.StaffRole;
import com.se203730.mvc.generateuibe.features.admin.entity.User;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class AdminStaffService {
    @Autowired
    private UserRepository users;

    @Autowired
    private AccountAccessService accounts;

    @Autowired
    private SupabaseAuthClient supabase;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private DatabaseClock clock;

    @Transactional(readOnly = true)
    public Page<StaffResponse> list(
            UUID adminId,
            StaffRole role,
            String keyword,
            int page,
            int size) {
        accounts.requireAdmin(adminId);
        List<UserRole> roles = role == null
                ? List.of(UserRole.ECOMMERCE_EXECUTIVE, UserRole.LOGISTICS_STAFF,
                        UserRole.DELIVERY_STAFF)
                : List.of(role.userRole());
        String search = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        String phone = PhoneNumberUtils.normalize(search);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));
        return users.findStaff(roles, pattern(search), pattern(phone == null ? search : phone),
                PageRequest.of(page, size, sort)).map(StaffResponse::from);
    }

    public StaffResponse create(UUID adminId, CreateStaffRequest request) {
        accounts.requireAdmin(adminId);
        if (!Objects.equals(request.password(), request.confirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (users.existsByPhone(request.phone())) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        UUID id = supabase.createStaffUser(request.email(), request.password());
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                Instant now = clock.now();
                User staff = new User();
                staff.setId(id);
                staff.setFirstName(request.firstName());
                staff.setLastName(request.lastName());
                staff.setEmail(request.email());
                staff.setPhone(request.phone());
                staff.setRole(request.role().userRole());
                staff.setEmailVerified(true);
                staff.setBanned(false);
                staff.setCreatedAt(now);
                staff.setUpdatedAt(now);
                users.saveAndFlush(staff);
                return StaffResponse.from(staff);
            }));
        } catch (RuntimeException exception) {
            // Chỉ dọn Auth user do lần tạo này sinh ra khi transaction nội bộ thất bại.
            try {
                supabase.deleteUser(id);
            } catch (RuntimeException cleanupFailure) {
                log.error("Could not remove new staff Auth user {} after database rollback", id);
            }
            throw exception;
        }
    }

    private static String pattern(String value) {
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
