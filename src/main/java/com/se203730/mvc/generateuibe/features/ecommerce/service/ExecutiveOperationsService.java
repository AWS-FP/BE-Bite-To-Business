package com.se203730.mvc.generateuibe.features.ecommerce.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.ExecutiveQuoteDecisionRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.ExecutiveReviewDecisionRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.EcommercePageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.EcommerceExecutiveRepository;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.customer.service.NotificationEventPublisher;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteItemRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.QuotePackageRepository;
import com.se203730.mvc.generateuibe.features.customer.service.QuoteWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class ExecutiveOperationsService {
    private final EcommerceExecutiveRepository db;
    private final QuoteRepository quotes;
    private final QuoteItemRepository items;
    private final QuotePackageRepository packages;
    private final QuoteWorkflowService workflow;
    private final DatabaseClock clock;
    private final NotificationEventPublisher notificationEvents;
    private static final Set<String> QUOTE_STATES=Set.of(
        "DRAFT","PENDING_APPROVAL","APPROVED","REJECTED","ACCEPTED","EXPIRED","CANCELLED");

    @Transactional(readOnly=true)
    public EcommercePageResponse<Map<String,Object>> quotes(String status,int page,int size) {
        if(status!=null && !QUOTE_STATES.contains(status)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var p=new MapSqlParameterSource().addValue("status",status);
        String where=status==null? "" : " where q.status=:status";
        return db.page("select q.id,q.quote_number,q.customer_id,q.status,q.revision,q.company_name," +
            "q.contact_name,q.items_subtotal,q.volume_discount_amount,q.sale_discount_amount," +
            "q.coupon_discount_amount,q.shipping_fee,q.vat_amount,q.total_amount," +
            "q.reviewed_by,q.reviewed_revision,q.reviewed_at,q.review_note,q.submitted_at," +
            "q.expires_at,q.created_at,q.updated_at from public.quotes q"+where+
            " order by q.created_at desc,q.id", "select count(*) from public.quotes q"+where,p,page,size);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> quote(UUID id) {
        var p=new MapSqlParameterSource("id",id);
        var quote=db.one("select id,quote_number,customer_id,status,revision,contact_name,contact_phone," +
            "contact_email,company_name,note,customer_po_reference,items_subtotal,volume_discount_amount," +
            "sale_discount_amount,coupon_discount_amount,shipping_fee,shipping_coupon_discount_amount," +
            "vat_amount,total_amount,reviewed_by,reviewed_revision,reviewed_at,review_note,submitted_at," +
            "expires_at,created_at,updated_at from public.quotes where id=:id",p);
        quote.put("items",db.list("select id,product_id,sku,name_vi,name_en,unit_code,quantity," +
            "list_unit_price,volume_unit_price,sale_unit_price,coupon_discount_amount,vat_rate," +
            "line_subtotal,volume_discount_amount,sale_discount_amount,vat_amount,line_total " +
            "from public.quote_items where quote_id=:id order by created_at,id",p));
        quote.put("revisions",db.list("select revision,change_note,created_by,created_at " +
            "from public.quote_revisions where quote_id=:id order by revision desc",p));
        return quote;
    }
    /** Read-only diagnostics, NOT authorization to approve a quote. */
    @Transactional(readOnly=true)
    public Map<String,Object> quoteReadiness(UUID id) {
        var p = new MapSqlParameterSource("id", id);
        var q = db.one("select status,revision,(expires_at is null or expires_at > now()) as unexpired from public.quotes where id=:id", p);
        Map<String,Object> checks = new LinkedHashMap<>();
        checks.put("submitted", "PENDING_APPROVAL".equals(q.get("status")));
        checks.put("hasItems", db.queryForObject(
            "select exists(select 1 from public.quote_items where quote_id=:id)",p,Boolean.class));
        checks.put("vatComplete", db.queryForObject(
            "select not exists(select 1 from public.quote_items where quote_id=:id " +
                "and vat_rate_configured=false)",p,Boolean.class));
        checks.put("hasValidPackages", db.queryForObject(
            "select exists(select 1 from public.quote_packages where quote_id=:id " +
                "and cancelled_at is null) and not exists(select 1 from public.quote_packages " +
                "where quote_id=:id and cancelled_at is null and address_status<>'VALID')",
            p,Boolean.class));
        checks.put("allocationComplete", db.queryForObject(
            "select not exists(select 1 from public.quote_items qi " +
                "where qi.quote_id=:id and qi.quantity <> coalesce((select sum(qpi.quantity) " +
                "from public.quote_package_items qpi join public.quote_packages qp " +
                "on qp.id=qpi.quote_package_id where qpi.quote_item_id=qi.id " +
                "and qp.cancelled_at is null),0))",p,Boolean.class));
        checks.put("stockAvailable", db.queryForObject(
            "select not exists(select 1 from public.quote_items qi " +
                "left join public.products pr on pr.id=qi.product_id " +
                "left join public.inventory inv on inv.product_id=qi.product_id " +
                "where qi.quote_id=:id and (coalesce(inv.available_quantity,0)<qi.quantity " +
                "or pr.status<>'PUBLISHED'))",p,Boolean.class));
        boolean eligible = Boolean.TRUE.equals(q.get("unexpired")) && checks.values().stream().allMatch(Boolean.TRUE::equals);
        return Map.of("quoteId",id,"status",q.get("status"),
            "revision",q.get("revision"),"checks",checks,"approvalEnabled",eligible);
    }
    @Transactional
    public Map<String,Object> decideQuote(UUID id,UUID staff,ExecutiveQuoteDecisionRequest r) {
        boolean approve = "APPROVED".equals(r.decision());
        if (!approve && (!"REJECTED".equals(r.decision()) || r.reason() == null || r.reason().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var quote = quotes.findForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (quote.getRevision() != r.expectedRevision()) throw new BusinessException(ErrorCode.QUOTE_REVISION_CONFLICT);
        if (!"PENDING_APPROVAL".equals(quote.getStatus())) throw new BusinessException(ErrorCode.CONFLICT);
        var now = clock.now();
        if (approve) {
            if (!Boolean.TRUE.equals(quoteReadiness(id).get("approvalEnabled")) || quote.getTotalAmount().signum() <= 0) {
                throw new BusinessException(ErrorCode.QUOTE_INCOMPLETE);
            }
            workflow.validateQuote(quote, items.findAllByQuoteIdOrderByCreatedAtAsc(id),
                packages.findAllByQuoteIdAndCancelledAtIsNullOrderByCreatedAtAsc(id), now);
        }
        quote.setStatus(r.decision());
        quote.setReviewedBy(staff);
        quote.setReviewedRevision(r.expectedRevision());
        quote.setReviewedAt(now);
        quote.setReviewNote(r.reason() == null ? null : r.reason().strip());
        quote.setUpdatedAt(now);
        quotes.saveAndFlush(quote);
        String reference = quote.getQuoteNumber();
        notificationEvents.publish(quote.getCustomerId(), "QUOTE_APPROVAL", id, "QUOTE",
            approve ? "Báo giá đã được duyệt" : "Báo giá bị từ chối",
            approve ? "Your quote was approved" : "Your quote was rejected",
            "Báo giá " + reference + (approve ? " đã được phê duyệt." : " đã bị từ chối. Vui lòng xem chi tiết."),
            "Quote " + reference + (approve ? " has been approved." : " was rejected. View the details."),
            "/quotes");
        return quote(id);
    }
    @Transactional(readOnly=true)
    public EcommercePageResponse<Map<String,Object>> reviews(String status,Integer stars,int page,int size) {
        if(status!=null && !Set.of("VISIBLE","HIDDEN").contains(status))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (stars != null && (stars < 1 || stars > 5))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var p = new MapSqlParameterSource().addValue("hidden", "HIDDEN".equals(status))
            .addValue("stars", stars);
        StringBuilder where = new StringBuilder(" where 1=1");
        if (status != null) where.append(" and (r.deleted_at is not null)=:hidden");
        // Ratings support half-stars. E.g. 2 stars includes 2.0 and 2.5.
        if (stars != null) where.append(stars == 5 ? " and r.rating=5" :
            " and r.rating>=:stars and r.rating<(:stars+1)");
        return db.page("select r.id,r.product_id,r.customer_id,r.rating,r.content,r.deleted_at," +
                "r.deleted_by,r.deletion_reason,r.created_at,r.updated_at,p.name_vi as product_name_vi," +
                "p.name_en as product_name_en from public.product_reviews r " +
                "join public.products p on p.id=r.product_id" + where +
                " order by r.created_at desc,r.id",
            "select count(*) from public.product_reviews r" + where,p,page,size);
    }
    @Transactional
    public Map<String,Object> moderateReview(UUID id,UUID staff,ExecutiveReviewDecisionRequest r) {
        String action=r.action().strip().toUpperCase(Locale.ROOT);
        if(!Set.of("HIDE","RESTORE").contains(action)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var p=new MapSqlParameterSource("id",id).addValue("staff",staff);
        var current=db.one("select id,deleted_at,deleted_by from public.product_reviews " +
            "where id=:id for update",p);
        if("HIDE".equals(action)) {
            if(current.get("deletedAt")!=null) throw new BusinessException(ErrorCode.CONFLICT);
            if(r.reason()==null || r.reason().isBlank()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            db.update("update public.product_reviews set deleted_at=now(),deleted_by=:staff," +
                    "deletion_reason=:reason,updated_at=now() where id=:id",
                p.addValue("reason",r.reason().strip()));
        } else {
            // Prevent E-commerce from silently restoring a review deleted by a different actor.
            if(current.get("deletedAt")==null || !staff.equals(current.get("deletedBy")))
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            db.update("update public.product_reviews set deleted_at=null,deleted_by=null," +
                "deletion_reason=null,updated_at=now() where id=:id",p);
        }
        return db.one("select id,product_id,customer_id,rating,content,deleted_at,deleted_by," +
            "deletion_reason,created_at,updated_at from public.product_reviews where id=:id",p);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> productRequest(UUID id) {
        var p = new MapSqlParameterSource("id", id);
        var item = db.one("select r.id,r.customer_id,r.product_name,r.reference_url," +
            "r.contact_email,r.notify_by_email,r.status,r.customer_note,r.created_at,r.updated_at " +
            "from public.product_requests r where r.id=:id", p);
        item.put("responses", db.list("select id,response_note,product_name,selling_price," +
            "available_quantity,unit_code,email_status,created_at " +
            "from public.product_request_responses where request_id=:id order by created_at desc",p));
        return item;
    }
    @Transactional
    public Map<String,Object> startProductRequestReview(UUID id) {
        var p=new MapSqlParameterSource("id", id);
        int updated=db.update("update public.product_requests set status='REVIEWING'," +
            "updated_at=now() where id=:id and status='PENDING'",p);
        if (updated!=1) {
            db.one("select id from public.product_requests where id=:id",p);
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return productRequest(id);
    }
    @Transactional(readOnly=true)
    public EcommercePageResponse<Map<String,Object>> productRequests(String status,int page,int size) {
        if(status!=null && !Set.of("PENDING","REVIEWING","ADDED","REJECTED").contains(status))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var p=new MapSqlParameterSource("status",status);
        String where=status==null ? "" : " where r.status=:status";
        // Logistics remains the owner of all response/write actions.
        return db.page("select r.id,r.customer_id,r.product_name,r.reference_url,r.status," +
            "r.customer_note,r.created_at,r.updated_at from public.product_requests r" + where +
            " order by r.created_at desc,r.id", "select count(*) from public.product_requests r"+where,p,page,size);
    }
}
