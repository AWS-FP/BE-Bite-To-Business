package com.se203730.mvc.generateuibe.features.delivery.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.repository.UserRepository;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.delivery.entity.DeliveryStaffAvailability;
import com.se203730.mvc.generateuibe.features.delivery.enums.DeliveryTripStatus;
import com.se203730.mvc.generateuibe.features.delivery.repository.DeliveryStaffAvailabilityRepository;
import com.se203730.mvc.generateuibe.features.delivery.repository.DeliveryTripRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DeliveryStaffAvailabilityService {
    @Autowired
    private DeliveryStaffAvailabilityRepository availability;
    @Autowired
    private DeliveryTripRepository trips;
    @Autowired
    private UserRepository users;
    @Autowired
    private AccountAccessService accounts;
    @Autowired
    private DatabaseClock clock;

    public record Staff(UUID userId, String firstName, String lastName, String phone, String status) {}

    @Transactional(readOnly = true)
    public List<Staff> list(UUID logisticsId) {
        if (accounts.requireActiveUser(logisticsId).getRole() != UserRole.LOGISTICS_STAFF) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return availability.findDeliveryStaff().stream()
                .map(row -> new Staff(row.getUserId(), row.getFirstName(), row.getLastName(), row.getPhone(), row.getStatus()))
                .toList();
    }

    @Transactional
    public String update(UUID staffId, String status) {
        users.findByIdForUpdate(staffId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (accounts.requireActiveUser(staffId).getRole() != UserRole.DELIVERY_STAFF) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (!"AVAILABLE".equals(status) && !"OFF_DUTY".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        boolean busy = trips.findByDeliveryStaffIdAndStatusIn(staffId,
                List.of(DeliveryTripStatus.PREPARING, DeliveryTripStatus.READY, DeliveryTripStatus.IN_DELIVERY)).isPresent();
        if (busy) throw new BusinessException(ErrorCode.CONFLICT);
        DeliveryStaffAvailability state = availability.findById(staffId).orElseGet(DeliveryStaffAvailability::new);
        state.setUserId(staffId);
        state.setStatus(status);
        state.setUpdatedAt(clock.now());
        availability.saveAndFlush(state);
        return status;
    }
}
