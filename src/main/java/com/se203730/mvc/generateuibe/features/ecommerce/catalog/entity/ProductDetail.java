
package com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product_details", schema = "public")
@Getter
@NoArgsConstructor
public class ProductDetail {

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "net_weight_kg")
    private BigDecimal netWeightKg;

    @Column(name = "tare_weight_kg")
    private BigDecimal tareWeightKg;

    @Column(name = "volume_m3")
    private BigDecimal volumeM3;

    @Column(name = "length_cm")
    private BigDecimal lengthCm;

    @Column(name = "width_cm")
    private BigDecimal widthCm;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    @Column(name = "technical_content_vi", columnDefinition = "text")
    private String technicalContentVi;

    @Column(name = "technical_content_en", columnDefinition = "text")
    private String technicalContentEn;

    @Column(name = "kitchen_storage_content_vi", columnDefinition = "text")
    private String kitchenStorageContentVi;

    @Column(name = "kitchen_storage_content_en", columnDefinition = "text")
    private String kitchenStorageContentEn;

    @Column(name = "compliance_vat_content_vi", columnDefinition = "text")
    private String complianceVatContentVi;

    @Column(name = "compliance_vat_content_en", columnDefinition = "text")
    private String complianceVatContentEn;

    @Column(name = "origin_content_vi", columnDefinition = "text")
    private String originContentVi;

    @Column(name = "origin_content_en", columnDefinition = "text")
    private String originContentEn;
}
