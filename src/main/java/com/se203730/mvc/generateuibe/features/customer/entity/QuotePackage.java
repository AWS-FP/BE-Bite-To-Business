package com.se203730.mvc.generateuibe.features.customer.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "quote_packages", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class QuotePackage {
    @Id
    private UUID id;
    private UUID quoteId;
    private String packageName;
    private LocalDate deliveryDate;
    private String deliveryAddress;
    private UUID createdBy;
    private Instant cancelledAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String recipientName;
    private String recipientPhone;
    private String deliveryTimeSlot;
    private String deliveryNote;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer distanceMetres;
    private BigDecimal shippingFee;
    private String addressStatus;
}
