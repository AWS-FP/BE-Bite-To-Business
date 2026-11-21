package com.se203730.mvc.generateuibe.features.logistics.repository;

import com.se203730.mvc.generateuibe.features.logistics.dto.CreateWarehouseProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WarehouseProductRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public void insert(UUID id, UUID userId, CreateWarehouseProductRequest r, String imageUrl, String imageId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", id).addValue("userId", userId).addValue("sku", r.sku().trim())
                .addValue("category", r.categoryId()).addValue("brand", r.brandId())
                .addValue("nameVi", r.nameVi().trim()).addValue("nameEn", r.nameEn().trim())
                .addValue("descriptionVi", r.descriptionVi()).addValue("descriptionEn", r.descriptionEn())
                .addValue("note", r.note()).addValue("origin", r.origin()).addValue("specVi", r.specVi())
                .addValue("specEn", r.specEn()).addValue("unit", r.unit()).addValue("minOrder", r.minOrder())
                .addValue("quantityStep", r.quantityStep()).addValue("vat", r.vat())
                .addValue("imageUrl", imageUrl).addValue("imageId", imageId).addValue("price", r.price())
                .addValue("netWeight", r.netWeight()).addValue("tareWeight", r.tareWeight()).addValue("volume", r.volume())
                .addValue("length", r.length()).addValue("width", r.width()).addValue("height", r.height())
                .addValue("technicalVi", r.technicalVi()).addValue("technicalEn", r.technicalEn())
                .addValue("storageVi", r.storageVi()).addValue("storageEn", r.storageEn())
                .addValue("complianceVi", r.complianceVi()).addValue("complianceEn", r.complianceEn())
                .addValue("originDetailVi", r.originDetailVi()).addValue("originDetailEn", r.originDetailEn());
        jdbc.update("""
                insert into public.products (id, sku, category_id, brand_id, name_vi, name_en,
                  description_vi, description_en, note, origin, package_spec_vi, package_spec_en,
                  unit_code, min_order_quantity, quantity_step, vat_rate, image_url, cloudinary_public_id, status)
                values (:id, :sku, :category, :brand, :nameVi, :nameEn, :descriptionVi, :descriptionEn,
                  :note, :origin, :specVi, :specEn, :unit, :minOrder, :quantityStep, :vat, :imageUrl, :imageId, 'DRAFT')
                """, p);
        jdbc.update("""
                insert into public.product_details (product_id, net_weight_kg, tare_weight_kg, volume_m3,
                  length_cm, width_cm, height_cm, technical_content_vi, technical_content_en,
                  kitchen_storage_content_vi, kitchen_storage_content_en, compliance_vat_content_vi,
                  compliance_vat_content_en, origin_content_vi, origin_content_en)
                values (:id, :netWeight, :tareWeight, :volume, :length, :width, :height, :technicalVi,
                  :technicalEn, :storageVi, :storageEn, :complianceVi, :complianceEn, :originDetailVi, :originDetailEn)
                """, p);
        jdbc.update("insert into public.product_prices (product_id, list_price) values (:id, :price)", p);
        jdbc.update("insert into public.inventory (product_id, updated_by) values (:id, :userId)", p);
        for (var tier : r.tiers()) {
            jdbc.update("""
                    insert into public.product_price_tiers (id, product_id, min_quantity, max_quantity, unit_price)
                    values (:tierId, :id, :min, :max, :tierPrice)
                    """, new MapSqlParameterSource().addValue("tierId", UUID.randomUUID()).addValue("id", id)
                    .addValue("min", tier.min()).addValue("max", tier.max()).addValue("tierPrice", tier.price()));
        }
    }
}
