package com.se203730.mvc.generateuibe.features.ecommerce.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.ExecutiveComboRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.EcommercePageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.EcommerceExecutiveRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

@Service
public class ExecutiveComboService {
    private final EcommerceExecutiveRepository db;
    public ExecutiveComboService(EcommerceExecutiveRepository db) { this.db = db; }

    private static final String SELECT = """
        select p.id,p.sku,p.name_vi,p.name_en,p.description_vi,p.description_en,p.category_id,
               p.image_url,p.cloudinary_public_id,p.vat_rate,p.status,c.serving_count,pp.list_price,
               (select count(*) from public.combo_items ci where ci.combo_id=p.id) item_count
        from public.combos c join public.products p on p.id=c.product_id
        left join public.product_prices pp on pp.product_id=p.id
        """;

    @Transactional(readOnly=true)
    public EcommercePageResponse<Map<String,Object>> list(String q,int page,int size) {
        String query=q==null ? "" : q.strip();
        if(query.length()>100) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var params=new MapSqlParameterSource();
        String where="";
        if(!query.isEmpty()) {
            params.addValue("q",query);
            where=" where catalog_search.fold_v1(:q) <> '' and (strpos(" +
                "catalog_search.fold_v1(concat_ws(' ',p.name_vi,p.name_en,p.sku)),catalog_search.fold_v1(:q)) > 0" +
                " or replace(catalog_search.fold_v1(p.sku),' ','')=replace(catalog_search.fold_v1(:q),' ',''))";
        }
        return db.page(SELECT+where+" order by p.created_at desc,p.id",
            "select count(*) from public.combos c join public.products p on p.id=c.product_id"+where,params,page,size);
    }

    @Transactional(readOnly=true)
    public EcommercePageResponse<Map<String,Object>> ingredients(String q,int page,int size) {
        String term=EcommerceExecutiveRepository.find(q,100);
        var params=new MapSqlParameterSource();
        String from=" from public.products p left join public.product_prices pp on pp.product_id=p.id where p.status='PUBLISHED' and p.unit_code<>'COMBO' ";
        String order=" order by p.name_vi,p.id";
        if(term!=null) {
            params.addValue("term",term);
            from=" from public.products p join catalog_search.ranked(:term,cast(null as uuid),cast(null as uuid[])) r on r.product_id=p.id left join public.product_prices pp on pp.product_id=p.id where p.status='PUBLISHED' and p.unit_code<>'COMBO' ";
            order=" order by r.relevance desc,p.id";
        }
        return db.page("select p.id,p.sku,p.name_vi,p.name_en,p.unit_code,p.min_order_quantity,p.quantity_step,p.image_url,p.status,pp.list_price"+from+order,"select count(*)"+from,params,page,size);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> categories() {
        return db.list("select id,name_vi,name_en from public.categories where is_active order by sort_order,name_vi,id",
            new MapSqlParameterSource());
    }

    @Transactional(readOnly=true)
    public Map<String,Object> detail(UUID id) {
        var p=new MapSqlParameterSource("id",id);
        var result=db.one(SELECT+" where p.id=:id",p);
        var details = db.list("""
            select net_weight_kg,tare_weight_kg,volume_cm3,length_cm,width_cm,height_cm,
                   technical_content_vi,technical_content_en,kitchen_storage_content_vi,
                   kitchen_storage_content_en,origin_content_vi,origin_content_en
            from public.product_details where product_id=:id
            """,p);
        result.put("details", details.isEmpty() ? null : details.getFirst());
        result.put("items",db.list("""
            select ci.product_id,p.sku,p.name_vi,p.name_en,p.unit_code,p.min_order_quantity,p.quantity_step,
                   p.image_url,p.status,ci.quantity,pp.list_price
            from public.combo_items ci join public.products p on p.id=ci.product_id
            left join public.product_prices pp on pp.product_id=p.id
            where ci.combo_id=:id order by p.name_vi,p.id
            """,p));
        return result;
    }

    private void validateItems(List<ExecutiveComboRequest.Item> items) {
        var ids=new TreeSet<UUID>();
        for(var item:items) if(!ids.add(item.productId())) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var rows=db.list("""
            select id,unit_code,status,quantity_step from public.products
            where id in (:ids) order by id for share
            """,new MapSqlParameterSource("ids",ids));
        if(rows.size()!=ids.size()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var quantities=new HashMap<UUID,BigDecimal>();
        items.forEach(item -> quantities.put(item.productId(),item.quantity()));
        for(var row:rows) {
            BigDecimal q=quantities.get((UUID)row.get("id"));
            BigDecimal step=(BigDecimal)row.get("quantityStep");
            if(!"PUBLISHED".equals(row.get("status")) || "COMBO".equals(row.get("unitCode")) ||
                q==null || q.signum()<=0 || step==null || step.signum()<=0 || q.remainder(step).signum()!=0)
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private MapSqlParameterSource params(ExecutiveComboRequest r) {
        if(r.imageUrl()!=null && !r.imageUrl().isBlank()) {
            try {
                URI uri=URI.create(r.imageUrl());
                if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)
                    throw new IllegalArgumentException();
            } catch(IllegalArgumentException ex) { throw new BusinessException(ErrorCode.INVALID_REQUEST); }
        }
        var p=new MapSqlParameterSource("category",r.categoryId());
        db.one("select id from public.categories where id=:category and is_active for share",p);
        validateItems(r.items());
        return p.addValue("vi",r.nameVi().strip())
            .addValue("en",r.nameEn().strip()).addValue("descVi",r.descriptionVi()).addValue("descEn",r.descriptionEn())
            .addValue("servings",r.servingCount()).addValue("price",r.listPrice()).addValue("vat",r.vatRate())
            .addValue("image",r.imageUrl()==null || r.imageUrl().isBlank()?null:r.imageUrl())
            .addValue("publicId",r.cloudinaryPublicId());
    }

    @Transactional
    public Map<String,Object> create(ExecutiveComboRequest r) {
        var p=params(r);
        UUID id=null;
        // nextval is atomic across concurrent requests; a reserved number is never reused.
        // Skip a matching code if an external import has already occupied it.
        for(int attempt=0;attempt<32 && id==null;attempt++) {
            Long number=db.queryForObject("select nextval('public.combo_sku_seq')",new MapSqlParameterSource(),Long.class);
            String sku=String.format(Locale.ROOT,"BTB-COMBO-%06d",number);
            p.addValue("sku",sku);
            var inserted=db.queryForList("""
                insert into public.products(sku,category_id,name_vi,name_en,description_vi,description_en,
                  unit_code,min_order_quantity,quantity_step,image_url,cloudinary_public_id,vat_rate,vat_updated_at,status)
                select :sku,:category,:vi,:en,:descVi,:descEn,'COMBO',1,1,:image,:publicId,:vat,now(),'DRAFT'
                where not exists (select 1 from public.products where upper(sku)=:sku)
                on conflict (sku) do nothing returning id
                """,p,UUID.class);
            if(!inserted.isEmpty()) id=inserted.getFirst();
        }
        if(id==null) throw new BusinessException(ErrorCode.CONFLICT);
        p.addValue("id",id);
        db.update("insert into public.combos(product_id,serving_count) values(:id,:servings)",p);
        db.update("insert into public.product_prices(product_id,list_price) values(:id,:price)",p);
        insertItems(id,r.items());
        saveDetails(id,r.details());
        return detail(id);
    }

    private void saveDetails(UUID id, ExecutiveComboRequest.Details d) {
        // Older clients omit details; preserve the previously saved information.
        if (d == null) return;
        var p = new MapSqlParameterSource("id",id)
            .addValue("net",d.netWeightKg()).addValue("tare",d.tareWeightKg())
            .addValue("volume",d.volumeCm3()).addValue("length",d.lengthCm())
            .addValue("width",d.widthCm()).addValue("height",d.heightCm())
            .addValue("techVi",d.technicalContentVi()).addValue("techEn",d.technicalContentEn())
            .addValue("storageVi",d.kitchenStorageContentVi()).addValue("storageEn",d.kitchenStorageContentEn())
            .addValue("originVi",d.originContentVi()).addValue("originEn",d.originContentEn());
        db.update("""
            insert into public.product_details(product_id,net_weight_kg,tare_weight_kg,volume_cm3,
              length_cm,width_cm,height_cm,technical_content_vi,technical_content_en,
              kitchen_storage_content_vi,kitchen_storage_content_en,origin_content_vi,origin_content_en)
            values(:id,:net,:tare,:volume,:length,:width,:height,:techVi,:techEn,:storageVi,:storageEn,:originVi,:originEn)
            on conflict(product_id) do update set net_weight_kg=excluded.net_weight_kg,
              tare_weight_kg=excluded.tare_weight_kg,volume_cm3=excluded.volume_cm3,
              length_cm=excluded.length_cm,width_cm=excluded.width_cm,height_cm=excluded.height_cm,
              technical_content_vi=excluded.technical_content_vi,technical_content_en=excluded.technical_content_en,
              kitchen_storage_content_vi=excluded.kitchen_storage_content_vi,
              kitchen_storage_content_en=excluded.kitchen_storage_content_en,
              origin_content_vi=excluded.origin_content_vi,origin_content_en=excluded.origin_content_en,updated_at=now()
            """,p);
    }

    private void insertItems(UUID id,List<ExecutiveComboRequest.Item> items) {
        for(var item:items) db.update("insert into public.combo_items(combo_id,product_id,quantity) values(:id,:product,:quantity)",
            new MapSqlParameterSource("id",id).addValue("product",item.productId()).addValue("quantity",item.quantity()));
    }

    private Map<String,Object> lock(UUID id) {
        return db.one("select p.status,p.sku from public.products p join public.combos c on c.product_id=p.id where p.id=:id for update of p",
            new MapSqlParameterSource("id",id));
    }

    @Transactional
    public Map<String,Object> update(UUID id,ExecutiveComboRequest r) {
        var before=lock(id);
        if(!"DRAFT".equals(before.get("status")))
            throw new BusinessException(ErrorCode.CONFLICT);
        var p=params(r).addValue("id",id);
        db.update("""
            update public.products set category_id=:category,name_vi=:vi,name_en=:en,description_vi=:descVi,
              description_en=:descEn,image_url=:image,cloudinary_public_id=:publicId,
              vat_updated_at=case when vat_rate is distinct from :vat then now() else vat_updated_at end,vat_rate=:vat,updated_at=now()
            where id=:id
            """,p);
        // List prices of ordinary products stay under catalog administration. Only new combo composition is priced here.
        db.update("update public.combos set serving_count=:servings where product_id=:id",p);
        var prices=db.list("select list_price,sale_price,sale_discount_percent from public.product_prices where product_id=:id for update",p);
        if(prices.isEmpty()) {
            db.update("insert into public.product_prices(product_id,list_price) values(:id,:price)",p);
        } else if(r.listPrice().compareTo((BigDecimal)prices.getFirst().get("listPrice"))!=0) {
            var pricing=prices.getFirst();
            if(pricing.get("salePrice")!=null || pricing.get("saleDiscountPercent")!=null ||
                db.queryForObject("select count(*) from public.product_price_tiers where product_id=:id",p,Long.class)>0)
                throw new BusinessException(ErrorCode.CONFLICT);
            db.update("update public.product_prices set list_price=:price,updated_at=now() where product_id=:id",p);
        }
        db.update("delete from public.combo_items where combo_id=:id",p);
        insertItems(id,r.items());
        saveDetails(id,r.details());
        return detail(id);
    }

    @Transactional
    public Map<String,Object> status(UUID id,String status) {
        if(!Set.of("DRAFT","PUBLISHED","ARCHIVED").contains(status)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var before=lock(id);
        if("PUBLISHED".equals(status)) {
            if(!"DRAFT".equals(before.get("status"))) throw new BusinessException(ErrorCode.CONFLICT);
            var item=detail(id);
            if(!(item.get("imageUrl") instanceof String image) || image.isBlank() || item.get("vatRate")==null ||
               !(item.get("listPrice") instanceof BigDecimal price) || price.signum()<=0)
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            var p=new MapSqlParameterSource("id",id);
            db.one("select c.id from public.categories c join public.products p on p.category_id=c.id where p.id=:id and c.is_active for share of c",p);
            var rows=db.list("select product_id,quantity from public.combo_items where combo_id=:id",p);
            if(rows.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            validateItems(rows.stream().map(row -> new ExecutiveComboRequest.Item((UUID)row.get("productId"),(BigDecimal)row.get("quantity"))).toList());
        }
        db.update("update public.products set status=:status,updated_at=now() where id=:id",
            new MapSqlParameterSource("id",id).addValue("status",status));
        return detail(id);
    }
}
