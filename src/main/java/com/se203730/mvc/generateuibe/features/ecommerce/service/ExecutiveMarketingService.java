package com.se203730.mvc.generateuibe.features.ecommerce.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.ExecutiveCouponRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.ExecutivePromotionRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.ExecutivePromotionProductsRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.ExecutivePromotionProductFilterRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.EcommercePageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.EcommerceExecutiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class ExecutiveMarketingService {
    private final EcommerceExecutiveRepository db;

    @Transactional(readOnly = true)
    public EcommercePageResponse<Map<String,Object>> coupons(int page,int size) {
        return db.page("select c.id,c.code,c.discount_type,c.discount_value,c.min_order_amount," +
                "c.max_discount_amount,c.starts_at,c.ends_at,c.usage_limit,c.usage_limit_per_user," +
                "c.is_active,c.applies_to,c.product_scope,c.created_by,c.created_at,c.updated_at," +
                "(select count(*) from public.orders o where (o.coupon_id=c.id or o.shipping_coupon_id=c.id) " +
                "and o.status<>'CANCELLED') as used_count from public.coupons c order by c.created_at desc,c.id",
            "select count(*) from public.coupons",new MapSqlParameterSource(),page,size);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> coupon(UUID id) {
        var p = new MapSqlParameterSource("id",id);
        var row = db.one("select id,code,discount_type,discount_value,min_order_amount,max_discount_amount," +
            "starts_at,ends_at,usage_limit,usage_limit_per_user,is_active,applies_to,product_scope,created_at " +
            "from public.coupons where id=:id",p);
        row.put("productIds",db.queryForList("select product_id from public.coupon_products " +
            "where coupon_id=:id",p,UUID.class));
        return row;
    }
    private static void validateCoupon(ExecutiveCouponRequest r) {
        if (!Set.of("PERCENTAGE","FIXED_AMOUNT").contains(r.discountType()) ||
            !Set.of("PRODUCT","SHIPPING").contains(r.appliesTo()) ||
            !Set.of("ALL","SELECTED").contains(r.productScope()) ||
            !r.endsAt().isAfter(r.startsAt()) ||
            ("PERCENTAGE".equals(r.discountType()) && r.discountValue().compareTo(BigDecimal.valueOf(100))>0))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        List<UUID> products = r.productIds()==null ? List.of() : r.productIds();
        if (products.size()>200 || products.size()!=new HashSet<>(products).size() ||
            ("SELECTED".equals(r.productScope()) && products.isEmpty()) ||
            ("SHIPPING".equals(r.appliesTo()) && !"ALL".equals(r.productScope())) ||
            ("ALL".equals(r.productScope()) && !products.isEmpty()))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    private MapSqlParameterSource couponParams(ExecutiveCouponRequest r) {
        return new MapSqlParameterSource().addValue("code",r.code().toUpperCase(Locale.ROOT))
            .addValue("type",r.discountType()).addValue("value",r.discountValue())
            .addValue("min",r.minOrderAmount()).addValue("max",r.maxDiscountAmount())
            .addValue("start",r.startsAt()).addValue("end",r.endsAt())
            .addValue("usage",r.usageLimit()).addValue("perUser",r.usageLimitPerUser())
            .addValue("active",r.isActive()).addValue("applies",r.appliesTo())
            .addValue("scope",r.productScope());
    }
    private void validateSelectedProducts(ExecutiveCouponRequest req) {
        if (!"SELECTED".equals(req.productScope())) return;
        var p=new MapSqlParameterSource("ids",req.productIds());
        Long n=db.queryForObject("select count(*) from public.products " +
            "where id in (:ids) and status='PUBLISHED'",p,Long.class);
        if (n==null || n!=req.productIds().size()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    private void replaceCouponProducts(UUID id,ExecutiveCouponRequest r) {
        db.update("delete from public.coupon_products where coupon_id=:id",new MapSqlParameterSource("id",id));
        if ("SELECTED".equals(r.productScope()))
            for(UUID product:r.productIds()) db.update(
                "insert into public.coupon_products(coupon_id,product_id) values(:id,:product)",
                new MapSqlParameterSource("id",id).addValue("product",product));
    }
    @Transactional
    public Map<String,Object> createCoupon(UUID staffId,ExecutiveCouponRequest r) {
        validateCoupon(r);validateSelectedProducts(r);
        var p=couponParams(r).addValue("staff",staffId);
        try {
            UUID id=db.queryForObject("insert into public.coupons " +
                "(code,discount_type,discount_value,min_order_amount,max_discount_amount," +
                "starts_at,ends_at,usage_limit,usage_limit_per_user,is_active,applies_to,product_scope,created_by) " +
                "values(:code,:type,:value,:min,:max,:start,:end,:usage,:perUser,:active,:applies,:scope,:staff) " +
                "returning id",p,UUID.class);
            replaceCouponProducts(id,r);
            return coupon(id);
        } catch(DuplicateKeyException ex) {throw new BusinessException(ErrorCode.CONFLICT,ex);}
    }
    @Transactional
    public Map<String,Object> updateCoupon(UUID id,ExecutiveCouponRequest r) {
        validateCoupon(r); validateSelectedProducts(r);
        var p=couponParams(r).addValue("id",id);
        db.one("select id from public.coupons where id=:id for update",p);
        // No retroactive changes to confirmed order snapshots; new quotes use the current rules.
        try {
            db.update("update public.coupons set code=:code,discount_type=:type,discount_value=:value," +
                "min_order_amount=:min,max_discount_amount=:max,starts_at=:start,ends_at=:end," +
                "usage_limit=:usage,usage_limit_per_user=:perUser,is_active=:active," +
                "applies_to=:applies,product_scope=:scope,updated_at=now() where id=:id",p);
        } catch(DuplicateKeyException ex) {throw new BusinessException(ErrorCode.CONFLICT,ex);}
        replaceCouponProducts(id,r);
        return coupon(id);
    }
    @Transactional
    public Map<String,Object> setCouponActive(UUID id,boolean active) {
        var p=new MapSqlParameterSource("id",id).addValue("active",active);
        int updated=db.update("update public.coupons set is_active=:active,updated_at=now() where id=:id",p);
        if(updated==0) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return coupon(id);
    }

    @Transactional(readOnly=true)
    public EcommercePageResponse<Map<String,Object>> promotions(String status,String q,int page,int size) {
        if (status!=null && !Set.of("DRAFT","PUBLISHED","DISABLED","ARCHIVED").contains(status))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        String query=q==null ? "" : q.strip();
        if (query.length()>100) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var p = new MapSqlParameterSource("status",status)
            .addValue("search", query.isEmpty()?"%%":"%"+EcommerceExecutiveRepository.find(query,100)+"%");
        String where = " where 1=1";
        if(status!=null) where += " and p.status=:status";
        if(!query.isEmpty()) where += " and (p.name_vi ILIKE :search ESCAPE '\\' " +
            "or p.name_en ILIKE :search ESCAPE '\\')";
        return db.page("select p.id,p.name_vi,p.name_en,p.content_vi,p.content_en,p.status," +
                "p.starts_at,p.ends_at,p.promotion_type,p.image_url,p.created_by,p.published_at,p.created_at,p.updated_at " +
                "from public.promotions p" + where + " order by p.created_at desc,p.id",
            "select count(*) from public.promotions p"+where,p,page,size);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> promotion(UUID id) {
        return db.one("select id,name_vi,name_en,content_vi,content_en,status,starts_at,ends_at," +
                "promotion_type,image_url,created_by,published_at,created_at,updated_at from public.promotions where id=:id",
            new MapSqlParameterSource("id",id));
    }
    /** Delete only campaigns never published, preserving customers' historical promotions. */
    @Transactional
    public void deletePromotion(UUID id) {
        var params=new MapSqlParameterSource("id",id);
        var campaign=db.one("select status,published_at from public.promotions " +
            "where id=:id for update",params);
        if (!Set.of("DRAFT","ARCHIVED").contains(campaign.get("status")) ||
            campaign.get("publishedAt")!=null) throw new BusinessException(ErrorCode.CONFLICT);
        Boolean hasHistory=db.queryForObject("select exists(select 1 from " +
            "public.promotion_email_deliveries where promotion_id=:id)",params,Boolean.class);
        if(Boolean.TRUE.equals(hasHistory)) throw new BusinessException(ErrorCode.CONFLICT);
        // promotion_products is the only product link (V41) and is ON DELETE CASCADE.
        int deleted=db.update("delete from public.promotions where id=:id " +
            "and published_at is null and status in ('DRAFT','ARCHIVED')",params);
        if(deleted!=1) throw new BusinessException(ErrorCode.CONFLICT);
    }

    private static String promotionType(ExecutivePromotionRequest r) {
        return r.promotionType() == null ? "STANDARD" : r.promotionType();
    }
    private static void validatePromotion(ExecutivePromotionRequest r) {
        if (!Set.of("STANDARD", "FLASH_SALE").contains(promotionType(r)) ||
            ("FLASH_SALE".equals(promotionType(r)) &&
                java.time.Duration.between(r.startsAt(),r.endsAt()).compareTo(java.time.Duration.ofHours(72))>0) ||
            !r.endsAt().isAfter(r.startsAt()) || (r.contentVi()!=null && r.contentVi().isBlank()) ||
            (r.contentEn()!=null && r.contentEn().isBlank()) ||
            (r.imageUrl()!=null && (r.imageUrl().length()>2048 || !r.imageUrl().startsWith("https://"))))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    private MapSqlParameterSource promotionParams(ExecutivePromotionRequest r) {
        return new MapSqlParameterSource().addValue("vi",r.nameVi().strip()).addValue("en",r.nameEn().strip())
            .addValue("contentVi",r.contentVi()).addValue("contentEn",r.contentEn())
            .addValue("start",r.startsAt()).addValue("end",r.endsAt()).addValue("image",r.imageUrl())
            .addValue("type",promotionType(r));
    }
    @Transactional
    public Map<String,Object> createPromotion(UUID staffId,ExecutivePromotionRequest r) {
        validatePromotion(r);
        var p=promotionParams(r).addValue("staff",staffId);
        UUID id=db.queryForObject("insert into public.promotions " +
            "(name_vi,name_en,content_vi,content_en,starts_at,ends_at,image_url,created_by,status,promotion_type)" +
            "values(:vi,:en,:contentVi,:contentEn,:start,:end,:image,:staff,'DRAFT',:type) returning id",p,UUID.class);
        return promotion(id);
    }
    @Transactional
    public Map<String,Object> updatePromotion(UUID id,ExecutivePromotionRequest r) {
        validatePromotion(r);
        var p=promotionParams(r).addValue("id",id);
        var before=db.one("select status from public.promotions where id=:id for update",p);
        if ("ARCHIVED".equals(before.get("status"))) throw new BusinessException(ErrorCode.CONFLICT);
        if ("FLASH_SALE".equals(promotionType(r)) && promotionProductIds(id).size()>8)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        // Every edit to a published campaign returns it to DRAFT for manual review.
        db.update("update public.promotions set name_vi=:vi,name_en=:en,content_vi=:contentVi," +
            "content_en=:contentEn,starts_at=:start,ends_at=:end,image_url=:image,promotion_type=:type," +
            "status='DRAFT',updated_at=now() where id=:id",p);
        return promotion(id);
    }

    /** Current assignments; promotion_products is the single source of truth since V41. */
    @Transactional(readOnly=true)
    public List<UUID> promotionProductIds(UUID id) {
        promotion(id);
        return db.queryForList("""
            SELECT product_id FROM public.promotion_products WHERE promotion_id=:id
            ORDER BY product_id
            """,new MapSqlParameterSource("id",id),UUID.class);
    }

    /** Shared projection for both the browser and the selected-products pane. */
    private static final String PROMOTION_PRODUCT_COLUMNS = """
        SELECT p.id,p.sku,p.name_vi,p.name_en,p.image_url,p.status,p.unit_code,
            p.brand_id,p.category_id,p.min_order_quantity,
            CAST(NULL AS uuid) AS legacy_promotion_id,CAST(NULL AS varchar) AS legacy_promotion_name,
            (COALESCE(cat.is_active,FALSE) AND
              (cat.parent_id IS NULL OR COALESCE(parent.is_active,FALSE))) AS category_active,
            price.list_price,price.sale_price,price.sale_discount_percent,
            GREATEST(COALESCE(stock.available_quantity,0),0) AS available_quantity,
            EXISTS(SELECT 1 FROM public.promotion_products pm
                WHERE pm.product_id=p.id AND pm.promotion_id=:id) AS selected
        """;

    private static final String PROMOTION_PRODUCT_FROM = """
        FROM public.products p
        LEFT JOIN public.product_prices price ON price.product_id=p.id
        LEFT JOIN public.inventory stock ON stock.product_id=p.id
        LEFT JOIN public.categories cat ON cat.id=p.category_id
        LEFT JOIN public.categories parent ON parent.id=cat.parent_id
        WHERE 1=1
        """;

    private static MapSqlParameterSource filters(UUID campaignId, ExecutivePromotionProductFilterRequest f) {
        String q = f.q() == null ? "" : f.q().strip();
        if (q.length() > 100) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return new MapSqlParameterSource("id", campaignId)
            .addValue("q", q)
            .addValue("brand", f.brandId())
            .addValue("category", f.categoryId())
            .addValue("stockOnly", f.inStockOnly())
            .addValue("discountOnly", f.discountOnly());
    }

    // This predicate is shared by the visible product picker AND the bulk-select
    // endpoint. Never use fuzzy ranking to expand a bulk price/selection scope.
    // Punctuation/accents are normalized only for non-accented queries; explicitly
    // accented searches remain literal to avoid confusing different Vietnamese words.
    private static final String PROMOTION_PRODUCT_FILTER = """
        AND (:q = '' OR CASE
            WHEN octet_length(:q) > char_length(:q) THEN
                position(lower(:q) IN lower(p.name_vi || ' ' || p.name_en || ' ' || p.sku)) > 0
            ELSE
                coalesce((
                    SELECT bool_and(
                        position(' ' || token IN ' ' ||
                            catalog_search.fold_v1(p.name_vi || ' ' || p.name_en || ' ' || p.sku)) > 0
                    )
                    FROM unnest(string_to_array(catalog_search.fold_v1(:q), ' ')) AS token
                    WHERE token <> ''
                ), false)
                OR (length(catalog_search.fold_v1(:q)) >= 3 AND
                    position(replace(catalog_search.fold_v1(:q), ' ', '') IN
                        replace(catalog_search.fold_v1(p.name_vi || ' ' || p.name_en || ' ' || p.sku), ' ', '')) > 0)
            END)
        AND (NOT :stockOnly OR COALESCE(stock.available_quantity,0)>=p.min_order_quantity)
        AND (NOT :discountOnly OR (price.list_price>0 AND price.sale_price>=0
            AND price.sale_price<price.list_price))
        """;

    /** Avoid binding untyped UUID nulls to `IS NULL` (PostgreSQL error 42P18). */
    private static String productFilterSql(ExecutivePromotionProductFilterRequest f) {
        var sql=new StringBuilder(PROMOTION_PRODUCT_FILTER);
        if (f.brandId()!=null) sql.append(" AND p.brand_id=:brand\n");
        if (f.categoryId()!=null)
            sql.append(" AND (p.category_id=:category OR cat.parent_id=:category)\n");
        return sql.toString();
    }

    /** Server-side filters search the entire catalog, never only a currently loaded page. */
    @Transactional(readOnly=true)
    public EcommercePageResponse<Map<String,Object>> promotionProductChoices(
        UUID id,ExecutivePromotionProductFilterRequest f,int page,int size) {
        promotion(id);
        var params=filters(id,f);
        String from=PROMOTION_PRODUCT_FROM+productFilterSql(f);
        return db.page(PROMOTION_PRODUCT_COLUMNS+from+"""
            ORDER BY EXISTS(SELECT 1 FROM public.promotion_products pm
                WHERE pm.product_id=p.id AND pm.promotion_id=:id) DESC,p.name_vi ASC,p.id ASC
            """, "SELECT count(*) "+from,params,page,size);
    }

    /** Resolve every locally selected ID, including products on other pages/filters. */
    @Transactional(readOnly=true)
    public List<Map<String,Object>> promotionProductDetails(UUID id,List<UUID> ids) {
        promotion(id);
        if (ids==null || ids.size()>2000 || ids.contains(null) ||
            new HashSet<>(ids).size()!=ids.size())
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (ids.isEmpty()) return List.of();
        return db.list(PROMOTION_PRODUCT_COLUMNS+PROMOTION_PRODUCT_FROM+
                " AND p.id IN (:ids) ORDER BY p.name_vi,p.id",
            new MapSqlParameterSource("id",id).addValue("ids",ids));
    }

    /** Bulk-select only eligible-to-assign products, without limiting to the visible page. */
    @Transactional(readOnly=true)
    public List<UUID> promotionMatchingProductIds(UUID id,ExecutivePromotionProductFilterRequest f) {
        promotion(id);
        var p=filters(id,f);
        List<UUID> matches=db.queryForList("SELECT p.id "+PROMOTION_PRODUCT_FROM+
            productFilterSql(f)+"""
                AND p.status='PUBLISHED' AND p.unit_code<>'COMBO'
                AND COALESCE(cat.is_active,FALSE)
                AND (cat.parent_id IS NULL OR COALESCE(parent.is_active,FALSE))
                ORDER BY p.name_vi,p.id LIMIT 2001
                """,p,UUID.class);
        if (matches.size()>2000) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return matches;
    }

    /** Validate the entire set under one transaction; never silently reassign a legacy campaign. */
    private boolean allFlashEligible(List<UUID> ids) {
        if (ids.isEmpty()) return false;
        Long eligible=db.queryForObject("""
            SELECT count(*) FROM public.products p
            JOIN public.product_prices pp ON pp.product_id=p.id
            JOIN public.categories cat ON cat.id=p.category_id AND cat.is_active=TRUE
            LEFT JOIN public.categories parent ON parent.id=cat.parent_id
            LEFT JOIN public.inventory inv ON inv.product_id=p.id
            WHERE p.id IN (:ids) AND p.status='PUBLISHED' AND p.unit_code<>'COMBO'
              AND (cat.parent_id IS NULL OR parent.is_active=TRUE)
              AND pp.list_price>0 AND pp.sale_price>=0 AND pp.sale_price<pp.list_price
              AND COALESCE(inv.available_quantity,0)>=p.min_order_quantity
            """,new MapSqlParameterSource("ids",ids),Long.class);
        return eligible!=null && eligible==ids.size();
    }

    /** One transaction for membership + requested price edits; no list-price or tier edits. */
    @Transactional
    public Map<String,Object> replacePromotionProducts(UUID id,ExecutivePromotionProductsRequest req) {
        List<UUID> requested=req.productIds();
        var priceChanges=req.priceUpdates()==null ? List.<ExecutivePromotionProductsRequest.PriceUpdate>of() : req.priceUpdates();
        if (requested==null || requested.size()>2000 || requested.contains(null)
            || new HashSet<>(requested).size()!=requested.size() || priceChanges.size()>2000)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        Set<UUID> requestedSet=new HashSet<>(requested);
        Set<UUID> pricedIds=new HashSet<>();
        for (var change:priceChanges) {
            if (change==null || change.productId()==null ||
                !requestedSet.contains(change.productId()) || !pricedIds.add(change.productId()) ||
                change.expectedListPrice()==null || change.expectedListPrice().signum()<=0 ||
                (change.newSalePrice()!=null && (change.newSalePrice().signum()<0 ||
                    change.newSalePrice().compareTo(change.expectedListPrice())>=0 ||
                    change.newSalePrice().stripTrailingZeros().scale()>0)))
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var p=new MapSqlParameterSource("id",id);
        var campaign=db.one("select id,status,promotion_type from public.promotions where id=:id for update",p);
        if ("ARCHIVED".equals(campaign.get("status"))) throw new BusinessException(ErrorCode.CONFLICT);
        List<UUID> previous=promotionProductIds(id);
        if (req.expectedProductIds()!=null && (
            req.expectedProductIds().contains(null) ||
                req.expectedProductIds().size()!=new HashSet<>(req.expectedProductIds()).size() ||
                !new HashSet<>(previous).equals(new HashSet<>(req.expectedProductIds()))))
            throw new BusinessException(ErrorCode.CONFLICT);
        boolean flash="FLASH_SALE".equals(campaign.get("promotionType"));
        if (flash && requested.size()>8) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (!requested.isEmpty()) {
            Long found=db.queryForObject("""
                SELECT count(*) FROM public.products p
                JOIN public.categories cat ON cat.id=p.category_id AND cat.is_active=TRUE
                LEFT JOIN public.categories parent ON parent.id=cat.parent_id
                WHERE p.id IN (:ids) AND p.status='PUBLISHED' AND p.unit_code<>'COMBO'
                  AND (cat.parent_id IS NULL OR parent.is_active=TRUE)
                """,new MapSqlParameterSource("ids",requested),Long.class);
            if (found==null || found!=requested.size()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        // Lock each price row in deterministic order, preventing lost updates from another editor.
        for (var change:priceChanges.stream()
            .sorted(Comparator.comparing(ExecutivePromotionProductsRequest.PriceUpdate::productId)).toList()) {
            var pp=db.one("select list_price,sale_price,sale_discount_percent from public.product_prices " +
                "where product_id=:pid for update",new MapSqlParameterSource("pid",change.productId()));
            BigDecimal actualList=(BigDecimal)pp.get("listPrice");
            BigDecimal actualSale=(BigDecimal)pp.get("salePrice");
            BigDecimal actualTierPercent=(BigDecimal)pp.get("saleDiscountPercent");
            if (actualList==null || actualList.compareTo(change.expectedListPrice())!=0 ||
                (actualSale==null ? change.expectedSalePrice()!=null :
                    change.expectedSalePrice()==null || actualSale.compareTo(change.expectedSalePrice())!=0) ||
                (actualTierPercent==null ? change.expectedSaleDiscountPercent()!=null :
                    change.expectedSaleDiscountPercent()==null ||
                        actualTierPercent.compareTo(change.expectedSaleDiscountPercent())!=0))
                throw new BusinessException(ErrorCode.CONFLICT);
            // Price is product-wide under the current schema. Do not change list price or tiers.
            db.update("update public.product_prices set sale_price=:sale," +
                    "sale_discount_percent=null,updated_at=now() where product_id=:pid",
                new MapSqlParameterSource("pid",change.productId())
                    .addValue("sale",change.newSalePrice()));
        }
        // Validate AFTER applying price edits, so a product can be selected and discounted in one save.
        if (flash && !requested.isEmpty() && !allFlashEligible(requested))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        boolean membershipChanged=!new HashSet<>(previous).equals(requestedSet);
        if (membershipChanged) {
            db.update("delete from public.promotion_products where promotion_id=:id",p);
            for(UUID productId:requested)
                db.update("insert into public.promotion_products(promotion_id,product_id) " +
                        "values(:id,:pid) on conflict do nothing",
                    new MapSqlParameterSource("id",id).addValue("pid",productId));
        }
        if (membershipChanged || !priceChanges.isEmpty())
            db.update("update public.promotions set status='DRAFT',updated_at=now() " +
                "where id=:id and status='PUBLISHED'",p);
        return promotion(id);
    }

    // PgJDBC may expose timestamptz as java.sql.Timestamp via ResultSet.getObject().
    private static java.time.Instant sqlInstant(Object value) {
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.time.Instant i) return i;
        throw new IllegalStateException("Unexpected promotion timestamp type: " +
            (value==null ? "null" : value.getClass().getName()));
    }

    @Transactional
    public Map<String,Object> setPromotionStatus(UUID id,String status) {
        if (!Set.of("PUBLISHED","DISABLED","ARCHIVED","DRAFT").contains(status))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var p=new MapSqlParameterSource("id",id).addValue("status",status);
        var existing=db.one("select status,content_vi,content_en,image_url,promotion_type,starts_at,ends_at,(ends_at>now()) as not_expired " +
            "from public.promotions where id=:id for update",p);
        if ("ARCHIVED".equals(existing.get("status")) != "DRAFT".equals(status)) throw new BusinessException(ErrorCode.CONFLICT);
        if ("PUBLISHED".equals(status)) {
            if ("FLASH_SALE".equals(existing.get("promotionType"))) {
                java.time.Instant starts=sqlInstant(existing.get("startsAt"));
                java.time.Instant ends=sqlInstant(existing.get("endsAt"));
                if (java.time.Duration.between(starts,ends).compareTo(java.time.Duration.ofHours(72))>0
                    || !ends.isAfter(starts)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
                var selected=promotionProductIds(id);
                if (selected.isEmpty() || selected.size()>8 || !allFlashEligible(selected))
                    throw new BusinessException(ErrorCode.CONFLICT);
            }
            if (existing.get("contentVi")==null || existing.get("contentEn")==null ||
                existing.get("imageUrl")==null ||
                !Boolean.TRUE.equals(existing.get("notExpired")))
                throw new BusinessException(ErrorCode.CONFLICT);
            db.update("update public.promotions set status='PUBLISHED',published_at=now(),updated_at=now() " +
                "where id=:id",p);
        } else {
            db.update("update public.promotions set status=:status,updated_at=now() where id=:id",p);
        }
        return promotion(id);
    }
}
