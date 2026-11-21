package com.se203730.mvc.generateuibe.features.ecommerce.executive.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.executive.repository.EcommerceExecutiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class ExecutiveDashboardService {
    private final EcommerceExecutiveRepository db;

    @Transactional(readOnly=true)
    public Map<String,Object> metrics(LocalDate from,LocalDate to) {
        if(from==null || to==null || from.isAfter(to) || to.isAfter(LocalDate.now().plusDays(1)) ||
            from.isBefore(to.minusYears(3)))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var p=new MapSqlParameterSource().addValue("from",from).addValue("to",to.plusDays(1));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("periodFrom",from);result.put("periodTo",to);
        result.putAll(db.one("select count(*) filter(where status='PUBLISHED') as published_products," +
                "count(*) filter(where status='DRAFT') as draft_products," +
                "count(*) filter(where status='ARCHIVED') as archived_products from public.products",
            new MapSqlParameterSource()));
        result.putAll(db.one("select count(*) filter(where status='PENDING_APPROVAL') as pending_quotes," +
                "count(*) filter(where status='APPROVED') as approved_quotes," +
                "count(*) filter(where status='REJECTED') as rejected_quotes from public.quotes",
            new MapSqlParameterSource()));
        result.putAll(db.one("select count(*) filter(where status='PUBLISHED' and starts_at<=now() " +
                "and ends_at>now()) as active_promotions from public.promotions",
            new MapSqlParameterSource()));
        result.putAll(db.one("select count(*) as completed_orders,coalesce(sum(total_amount),0) as revenue " +
            "from public.orders where status='COMPLETED' and completed_at>=:from " +
            "and completed_at<:to",p));
        result.putAll(db.one("select count(*) filter(where coupon_id is not null or shipping_coupon_id is not null) " +
            "as coupon_orders from public.orders where status<>'CANCELLED' and created_at>=:from " +
            "and created_at<:to",p));
        // Conversion denominator (issued/exposed coupons) is not yet tracked; do not fabricate a rate.
        result.put("couponConversionRate",null);
        return result;
    }
}
