package com.se203730.mvc.generateuibe.features.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "order_packages", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class OrderPackage {
    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "package_number", nullable = false, length = 50)
    private String packageNumber;

    @Column(name = "package_name", nullable = false, length = 200)
    private String packageName;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "delivery_address", nullable = false, columnDefinition = "text")
    private String deliveryAddress;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
