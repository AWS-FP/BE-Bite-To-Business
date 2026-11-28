package com.se203730.mvc.generateuibe.features.logistics.repository;

import com.se203730.mvc.generateuibe.features.logistics.dto.CreateWarehouseProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WarehouseProductRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public boolean archive(UUID id) {
        return jdbc.update("UPDATE public.products SET status='ARCHIVED', updated_at=CURRENT_TIMESTAMP WHERE id=:id AND unit_code <> 'COMBO'",
                new MapSqlParameterSource("id",id)) == 1;
    }
    public record EditPage(List<Map<String, Object>> items, long total, int totalPages) {}

    public EditPage editable(String keyword, int page, int size) {
        var p = new MapSqlParameterSource("q", keyword).addValue("limit", size)
                .addValue("offset", (long) page * size);
        String from = " from public.products p where p.status in ('DRAFT','PUBLISHED') and p.unit_code <> 'COMBO' ";
        String order = " order by p.name_vi, p.id ";
        if (!keyword.isBlank()) {
            from = """
                    from public.products p
                    left join catalog_search.ranked(:q) ranked on ranked.product_id=p.id
                    where p.status in ('DRAFT','PUBLISHED') and p.unit_code <> 'COMBO'
                    and catalog_search.fold_v1(:q) <> ''
                    and (ranked.product_id is not null or
                        starts_with(replace(catalog_search.fold_v1(p.sku),' ',''),replace(catalog_search.fold_v1(:q),' ','')) or
                        (p.status='DRAFT' and (replace(catalog_search.fold_v1(p.sku),' ','') = replace(catalog_search.fold_v1(:q),' ','')
                        or strpos(catalog_search.fold_v1(p.sku),catalog_search.fold_v1(:q)) > 0
                        or to_tsvector('simple',p.catalog_terms) @@ plainto_tsquery('simple',catalog_search.label_v2(:q))
                        or (lower(trim(:q)) ~ '^[a-z0-9 ,./×*_-]+$' and catalog_search.english_terms(p.name_en,p.package_spec_en,p.unit_code) @@ plainto_tsquery('english',catalog_search.label_v2(:q))))))
                    """;
            order = " order by case when lower(p.sku)=lower(:q) then 200 else coalesce(ranked.relevance,90) end desc, p.id ";
        }
        Long total = jdbc.queryForObject("select count(*)" + from, p, Long.class);
        long count = total == null ? 0 : total;
        var rows = jdbc.queryForList("select p.id,p.sku,p.name_vi as \"nameVi\",p.name_en as \"nameEn\",p.status,p.image_url as \"imageUrl\""
                + from + order + " limit :limit offset :offset", p);
        return new EditPage(rows, count, (int) ((count + size - 1) / size));
    }

    public Map<String, Object> editDetail(UUID id) {
        var p = new MapSqlParameterSource("id", id);
        var rows = jdbc.queryForList("""
                select p.id,p.sku,p.status,p.name_vi as "nameVi",p.name_en as "nameEn",
                  p.category_id as "categoryId",p.brand_id as "brandId",p.distributor_id as "distributorId",
                  p.origin,p.description_vi as "descriptionVi",p.description_en as "descriptionEn",p.note,
                  p.package_spec_vi as "specVi",p.package_spec_en as "specEn",p.unit_code as unit,
                  p.min_order_quantity as "minOrder",p.vat_rate as vat,p.image_url as "imageUrl",
                  pp.list_price as price,d.net_weight_kg as "netWeight",d.tare_weight_kg as "tareWeight",
                  d.volume_cm3 as volume,d.length_cm as length,d.width_cm as width,d.height_cm as height,
                  d.technical_content_vi as "technicalVi",d.technical_content_en as "technicalEn",
                  d.kitchen_storage_content_vi as "storageVi",d.kitchen_storage_content_en as "storageEn",
                  d.nutrition_basis as "nutritionBasis"
                from public.products p left join public.product_details d on d.product_id=p.id
                left join public.product_prices pp on pp.product_id=p.id
                where p.id=:id and p.status in ('DRAFT','PUBLISHED') and p.unit_code <> 'COMBO'
                """, p);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        Map<String, Object> result = rows.getFirst();
        result.put("tiers", jdbc.queryForList("""
                select id,min_quantity as min,max_quantity as max,unit_price as price
                from public.product_price_tiers where product_id=:id order by min_quantity
                """, p));
        result.put("nutritionFacts", jdbc.queryForList("""
                select nutrient,nutrient_en as "nutrientEn",amount from public.product_nutrition_facts
                where product_id=:id order by sort_order
                """, p));
        return result;
    }

    public String lockEditable(UUID id) {
        var rows = jdbc.queryForList("""
                select status from public.products where id=:id and status in ('DRAFT','PUBLISHED')
                and unit_code <> 'COMBO' for update
                """, new MapSqlParameterSource("id", id), String.class);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    private MapSqlParameterSource parameters(UUID id, UUID userId, CreateWarehouseProductRequest r, String imageUrl, String imageId) {
        return new MapSqlParameterSource()
                .addValue("id", id).addValue("userId", userId).addValue("sku", r.sku().trim())
                .addValue("category", r.categoryId()).addValue("brand", r.brandId())
                .addValue("distributor", r.distributorId())
                .addValue("nameVi", r.nameVi().trim()).addValue("nameEn", r.nameEn().trim())
                .addValue("descriptionVi", r.descriptionVi()).addValue("descriptionEn", r.descriptionEn())
                .addValue("note", r.note()).addValue("origin", r.origin()).addValue("specVi", r.specVi())
                .addValue("specEn", r.specEn()).addValue("unit", r.unit()).addValue("minOrder", r.minOrder())
                .addValue("quantityStep", java.math.BigDecimal.ONE).addValue("vat", r.vat())
                .addValue("imageUrl", imageUrl).addValue("imageId", imageId).addValue("price", r.price())
                .addValue("netWeight", r.netWeight()).addValue("tareWeight", r.tareWeight()).addValue("volume", r.volume())
                .addValue("length", r.length()).addValue("width", r.width()).addValue("height", r.height())
                .addValue("technicalVi", r.technicalVi()).addValue("technicalEn", r.technicalEn())
                .addValue("storageVi", r.storageVi()).addValue("storageEn", r.storageEn())
                .addValue("nutritionBasis", r.trimmedNutritionBasis());
    }

    public void insert(UUID id, UUID userId, CreateWarehouseProductRequest r, String imageUrl, String imageId) {
        var p = parameters(id, userId, r, imageUrl, imageId);
        jdbc.update("""
                insert into public.products (id, sku, category_id, brand_id, distributor_id, name_vi, name_en,
                  description_vi, description_en, note, origin, package_spec_vi, package_spec_en,
                  unit_code, min_order_quantity, quantity_step, vat_rate, image_url, cloudinary_public_id, status)
                values (:id, :sku, :category, :brand, :distributor, :nameVi, :nameEn, :descriptionVi, :descriptionEn,
                  :note, :origin, :specVi, :specEn, :unit, :minOrder, :quantityStep, :vat, :imageUrl, :imageId, 'DRAFT')
                """, p);
        // Nguồn gốc và "An toàn thực phẩm & VAT" không lưu văn bản: trang sản phẩm ghép từ origin, brand,
        // distributor và vat_rate để không trùng dữ liệu.
        jdbc.update("""
                insert into public.product_details (product_id, net_weight_kg, tare_weight_kg, volume_cm3,
                  length_cm, width_cm, height_cm, technical_content_vi, technical_content_en,
                  kitchen_storage_content_vi, kitchen_storage_content_en, compliance_vat_content_vi,
                  compliance_vat_content_en, origin_content_vi, origin_content_en, nutrition_basis)
                values (:id, :netWeight, :tareWeight, :volume, :length, :width, :height, :technicalVi,
                  :technicalEn, :storageVi, :storageEn, null, null, null, null,
                  :nutritionBasis)
                """, p);
        insertNutrition(id, r);
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
    private void insertNutrition(UUID id, CreateWarehouseProductRequest r) {
        List<CreateWarehouseProductRequest.NutritionFact> facts = r.nutritionFactsOrEmpty();
        if (!facts.isEmpty()) {
            // Một lệnh batch cho cả bảng dinh dưỡng; thứ tự dòng giữ đúng thứ tự người dùng nhập.
            MapSqlParameterSource[] rows = new MapSqlParameterSource[facts.size()];
            for (int i = 0; i < facts.size(); i++) {
                rows[i] = new MapSqlParameterSource().addValue("id", id).addValue("sortOrder", i)
                        .addValue("nutrient", facts.get(i).nutrient().strip())
                        .addValue("nutrientEn", facts.get(i).nutrientEn().strip())
                        .addValue("amount", facts.get(i).amount().strip());
            }
            jdbc.batchUpdate("""
                    insert into public.product_nutrition_facts (product_id, sort_order, nutrient, nutrient_en, amount)
                    values (:id, :sortOrder, :nutrient, :nutrientEn, :amount)
                    """, rows);
        }
    }

    public void update(UUID id, UUID userId, CreateWarehouseProductRequest r, String imageUrl, String imageId, boolean draft, boolean nutritionChanged, boolean tiersChanged) {
        var p = parameters(id, userId, r, imageUrl, imageId);
        jdbc.update("""
                update public.products set category_id=:category,brand_id=:brand,distributor_id=:distributor,
                  name_vi=:nameVi,name_en=:nameEn,description_vi=:descriptionVi,description_en=:descriptionEn,
                  note=:note,origin=:origin,package_spec_vi=:specVi,package_spec_en=:specEn,
                  image_url=coalesce(:imageUrl,image_url),cloudinary_public_id=coalesce(:imageId,cloudinary_public_id),
                  updated_at=now() where id=:id
                """, p);
        jdbc.update("""
                insert into public.product_details(product_id,net_weight_kg,tare_weight_kg,volume_cm3,length_cm,
                  width_cm,height_cm,technical_content_vi,technical_content_en,kitchen_storage_content_vi,
                  kitchen_storage_content_en,nutrition_basis)
                values (:id,:netWeight,:tareWeight,:volume,:length,:width,:height,:technicalVi,:technicalEn,
                  :storageVi,:storageEn,:nutritionBasis)
                on conflict (product_id) do update set net_weight_kg=excluded.net_weight_kg,
                  tare_weight_kg=excluded.tare_weight_kg,volume_cm3=excluded.volume_cm3,length_cm=excluded.length_cm,
                  width_cm=excluded.width_cm,height_cm=excluded.height_cm,technical_content_vi=excluded.technical_content_vi,
                  technical_content_en=excluded.technical_content_en,kitchen_storage_content_vi=excluded.kitchen_storage_content_vi,
                  kitchen_storage_content_en=excluded.kitchen_storage_content_en,nutrition_basis=excluded.nutrition_basis,updated_at=now()
                """, p);
        if (nutritionChanged) {
            jdbc.update("delete from public.product_nutrition_facts where product_id=:id", p);
            insertNutrition(id, r);
        }
        if (!draft) return;
        jdbc.update("""
                update public.products set min_order_quantity=:minOrder,
                  vat_updated_at=case when vat_rate is distinct from :vat then now() else vat_updated_at end,
                  vat_rate=:vat where id=:id
                """, p);
        if (r.price() != null) jdbc.update("""
                insert into public.product_prices(product_id,list_price) values (:id,:price)
                on conflict(product_id) do update set list_price=excluded.list_price,updated_at=now()
                """, p);
        if (!tiersChanged) return;
        // Preserve tier IDs for unchanged minimum quantities. Never remove a historical reference.
        var existing = jdbc.queryForList("select id,min_quantity from public.product_price_tiers where product_id=:id for update", p);
        var removed = existing.stream().filter(row -> r.tiers().stream().noneMatch(t ->
                t.min().compareTo((BigDecimal) row.get("min_quantity")) == 0)).map(row -> row.get("id")).toList();
        if (!removed.isEmpty()) {
            var tp = new MapSqlParameterSource("ids", removed);
            Boolean referenced = jdbc.queryForObject("""
                    select exists(select 1 from public.quote_items where price_tier_id in (:ids))
                    or exists(select 1 from public.order_items where price_tier_id in (:ids))
                    """, tp, Boolean.class);
            if (Boolean.TRUE.equals(referenced)) throw new BusinessException(ErrorCode.CONFLICT);
            jdbc.update("delete from public.product_price_tiers where id in (:ids)", tp);
        }
        // Shrink existing ranges before expanding them, to respect the exclusion constraint.
        jdbc.update("update public.product_price_tiers set max_quantity=min_quantity where product_id=:id", p);
        var rows = r.tiers().stream().map(t -> new MapSqlParameterSource("id", id)
                .addValue("min", t.min()).addValue("max", t.max()).addValue("price", t.price())).toArray(MapSqlParameterSource[]::new);
        if (rows.length > 0) jdbc.batchUpdate("""
                insert into public.product_price_tiers(product_id,min_quantity,max_quantity,unit_price)
                values(:id,:min,:max,:price) on conflict(product_id,min_quantity) do update
                set max_quantity=excluded.max_quantity,unit_price=excluded.unit_price,updated_at=now()
                """, rows);
    }
}
