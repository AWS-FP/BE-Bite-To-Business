package com.se203730.mvc.generateuibe.features.admin.registration.service;

import com.se203730.mvc.generateuibe.features.auth.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.auth.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.user.entity.User;
import com.se203730.mvc.generateuibe.features.user.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.user.enums.CustomerType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;

/** Lọc trên database trước khi phân trang; không chỉ tìm trong trang đang hiển thị. */
final class RegistrationFilters {
    private RegistrationFilters() {}

    static Specification<RegistrationRequest> matching(RegistrationStatus status, String keyword, CustomerType type) {
        return (root, query, builder) -> {
            var conditions = new ArrayList<Predicate>();
            if (status != null) conditions.add(builder.equal(root.get("status"), status));

            if (keyword != null && !keyword.isBlank()) {
                // Escape ký tự LIKE để %, _ và dấu escape được tìm như nội dung thông thường.
                String normalized = keyword.strip().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_");
                String pattern = "%" + normalized + "%";
                var customerQuery = query.subquery(Integer.class);
                var customer = customerQuery.from(User.class);
                var name = builder.concat(builder.concat(customer.get("lastName"), " "), customer.get("firstName"));
                var reverseName = builder.concat(builder.concat(customer.get("firstName"), " "), customer.get("lastName"));
                // public.users lưu +84; vẫn cho tìm bằng số nội địa bắt đầu bằng 0.
                String phoneKeyword = keyword.replaceAll("[\\s().-]", "");
                if (phoneKeyword.matches("0[0-9]+")) phoneKeyword = "+84" + phoneKeyword.substring(1);
                if (!phoneKeyword.matches("\\+?[0-9]+")) phoneKeyword = keyword.strip();
                String phonePattern = "%" + phoneKeyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                customerQuery.select(builder.literal(1)).where(
                        builder.equal(customer.get("id"), root.get("customerId")),
                        builder.or(builder.like(builder.lower(name), pattern, '!'),
                                builder.like(builder.lower(reverseName), pattern, '!'),
                                builder.like(builder.lower(customer.get("email")), pattern, '!'),
                                builder.like(customer.get("phone"), phonePattern, '!')));
                conditions.add(builder.exists(customerQuery));
            }

            if (type != null) {
                var profileQuery = query.subquery(Integer.class);
                var profile = profileQuery.from(UserProfile.class);
                profileQuery.select(builder.literal(1)).where(
                        builder.equal(profile.get("userId"), root.get("customerId")),
                        builder.equal(profile.get("customerType"), type));
                conditions.add(builder.exists(profileQuery));
            }
            return builder.and(conditions.toArray(Predicate[]::new));
        };
    }
}
