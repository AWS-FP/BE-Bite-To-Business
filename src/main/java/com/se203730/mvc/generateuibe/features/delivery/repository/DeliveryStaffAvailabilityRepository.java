package com.se203730.mvc.generateuibe.features.delivery.repository;

import com.se203730.mvc.generateuibe.features.delivery.entity.DeliveryStaffAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DeliveryStaffAvailabilityRepository extends JpaRepository<DeliveryStaffAvailability, UUID> {
    interface StaffRow {
        UUID getUserId();
        String getFirstName();
        String getLastName();
        String getPhone();
        String getStatus();
    }

    @Query(value = """
        select u.id as "userId", u.first_name as "firstName", u.last_name as "lastName", u.phone as phone,
            case when exists (select 1 from public.delivery_trips t where t.delivery_staff_id = u.id
                    and t.status in ('PREPARING', 'READY', 'IN_DELIVERY')) then 'BUSY'
                 else coalesce(a.status, 'OFF_DUTY') end as status
        from public.users u left join public.delivery_staff_availability a on a.user_id = u.id
        where u.role = 'DELIVERY_STAFF' and u.is_banned = false and u.is_email_verified = true
        order by u.last_name, u.first_name, u.id
        """, nativeQuery = true)
    List<StaffRow> findDeliveryStaff();
}
