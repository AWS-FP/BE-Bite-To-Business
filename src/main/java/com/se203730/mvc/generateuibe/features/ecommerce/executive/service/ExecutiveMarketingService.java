package com.se203730.mvc.generateuibe.features.ecommerce.executive.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.executive.dto.request.ExecutiveCouponRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.executive.dto.request.ExecutivePromotionRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.executive.dto.response.EcommercePageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.executive.repository.EcommerceExecutiveRepository;
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
    public EcommercePageResponse<Map<String,Object>> promotions(String status,int page,int size) {
        if (status!=null && !Set.of("DRAFT","PUBLISHED","DISABLED","ARCHIVED").contains(status))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var p = new MapSqlParameterSource("status",status);
        String where = status==null ? "" : " where p.status=:status";
        return db.page("select p.id,p.name_vi,p.name_en,p.content_vi,p.content_en,p.status," +
                "p.starts_at,p.ends_at,p.image_url,p.created_by,p.published_at,p.created_at,p.updated_at " +
                "from public.promotions p" + where + " order by p.created_at desc,p.id",
            "select count(*) from public.promotions p"+where,p,page,size);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> promotion(UUID id) {
        return db.one("select id,name_vi,name_en,content_vi,content_en,status,starts_at,ends_at," +
                "image_url,created_by,published_at,created_at,updated_at from public.promotions where id=:id",
            new MapSqlParameterSource("id",id));
    }
    private static void validatePromotion(ExecutivePromotionRequest r) {
        if (!r.endsAt().isAfter(r.startsAt()) || (r.contentVi()!=null && r.contentVi().isBlank()) ||
            (r.contentEn()!=null && r.contentEn().isBlank()) ||
            (r.imageUrl()!=null && (r.imageUrl().length()>2048 || !r.imageUrl().startsWith("https://"))))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    private MapSqlParameterSource promotionParams(ExecutivePromotionRequest r) {
        return new MapSqlParameterSource().addValue("vi",r.nameVi().strip()).addValue("en",r.nameEn().strip())
            .addValue("contentVi",r.contentVi()).addValue("contentEn",r.contentEn())
            .addValue("start",r.startsAt()).addValue("end",r.endsAt()).addValue("image",r.imageUrl());
    }
    @Transactional
    public Map<String,Object> createPromotion(UUID staffId,ExecutivePromotionRequest r) {
        validatePromotion(r);
        var p=promotionParams(r).addValue("staff",staffId);
        UUID id=db.queryForObject("insert into public.promotions " +
            "(name_vi,name_en,content_vi,content_en,starts_at,ends_at,image_url,created_by,status)" +
            "values(:vi,:en,:contentVi,:contentEn,:start,:end,:image,:staff,'DRAFT') returning id",p,UUID.class);
        return promotion(id);
    }
    @Transactional
    public Map<String,Object> updatePromotion(UUID id,ExecutivePromotionRequest r) {
        validatePromotion(r);
        var p=promotionParams(r).addValue("id",id);
        var before=db.one("select status from public.promotions where id=:id for update",p);
        if ("ARCHIVED".equals(before.get("status"))) throw new BusinessException(ErrorCode.CONFLICT);
        // Every edit to a published campaign returns it to DRAFT for manual review.
        db.update("update public.promotions set name_vi=:vi,name_en=:en,content_vi=:contentVi," +
            "content_en=:contentEn,starts_at=:start,ends_at=:end,image_url=:image," +
            "status='DRAFT',updated_at=now() where id=:id",p);
        return promotion(id);
    }
    @Transactional
    public Map<String,Object> setPromotionStatus(UUID id,String status) {
        if (!Set.of("PUBLISHED","DISABLED","ARCHIVED","DRAFT").contains(status))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var p=new MapSqlParameterSource("id",id).addValue("status",status);
        var existing=db.one("select status,content_vi,content_en,image_url,(ends_at>now()) as not_expired " +
            "from public.promotions where id=:id for update",p);
        if ("ARCHIVED".equals(existing.get("status")) != "DRAFT".equals(status)) throw new BusinessException(ErrorCode.CONFLICT);
        if ("PUBLISHED".equals(status)) {
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
