
package com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products", schema = "public")
@Getter
@NoArgsConstructor
public class Product {

    @Id
    private UUID id;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "name_vi", nullable = false)
    private String nameVi;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "description_vi", columnDefinition = "text")
    private String descriptionVi;

    @Column(name = "description_en", columnDefinition = "text")
    private String descriptionEn;

    @Column(name = "origin")
    private String origin;

    @Column(name = "package_spec_vi")
    private String packageSpecVi;

    @Column(name = "package_spec_en")
    private String packageSpecEn;

    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    @Column(name = "min_order_quantity", nullable = false)
    private BigDecimal minOrderQuantity;

    @Column(name = "quantity_step", nullable = false)
    private BigDecimal quantityStep;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(name = "cloudinary_public_id")
    private String cloudinaryPublicId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
