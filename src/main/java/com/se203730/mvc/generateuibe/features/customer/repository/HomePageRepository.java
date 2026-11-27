package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Customer/Public home read-model. No writes to staff ecommerce modules. */
@Repository
@RequiredArgsConstructor
public class HomePageRepository {
    private final JdbcTemplate jdbc;

    private static Instant instant(Timestamp t) {return t==null?null:t.toInstant();}

    // A1, A4, A6, A7: real PUBLISHED products. Sale price is visible only during an active promotion.
    private static final String PRODUCT_SELECT="""
 SELECT p.id AS product_id,p.sku,p.name_vi,p.name_en,p.image_url,p.unit_code,
   pp.list_price,
   CASE WHEN promo.status='PUBLISHED' AND promo.starts_at<=CURRENT_TIMESTAMP
              AND promo.ends_at>CURRENT_TIMESTAMP AND pp.sale_price>=0
              AND pp.sale_price<pp.list_price THEN pp.sale_price ELSE NULL END AS sale_price,
   GREATEST(COALESCE(inv.available_quantity,0),0) AS available_quantity,
   CASE WHEN promo.status='PUBLISHED' AND promo.starts_at<=CURRENT_TIMESTAMP
              AND promo.ends_at>CURRENT_TIMESTAMP AND pp.sale_price>=0
              AND pp.sale_price<pp.list_price THEN promo.id ELSE NULL END AS promotion_id,
   CASE WHEN promo.status='PUBLISHED' AND promo.starts_at<=CURRENT_TIMESTAMP
              AND promo.ends_at>CURRENT_TIMESTAMP AND pp.sale_price>=0
              AND pp.sale_price<pp.list_price THEN promo.name_vi ELSE NULL END AS promotion_name_vi,
   CASE WHEN promo.status='PUBLISHED' AND promo.starts_at<=CURRENT_TIMESTAMP
              AND promo.ends_at>CURRENT_TIMESTAMP AND pp.sale_price>=0
              AND pp.sale_price<pp.list_price THEN promo.ends_at ELSE NULL END AS promotion_ends_at,
   p.category_id,cat.name_vi AS category_name_vi,cat.name_en AS category_name_en,
   p.package_spec_vi,p.package_spec_en,p.min_order_quantity,p.quantity_step,p.vat_rate,
   ratings.average_rating,ratings.review_count
 FROM public.products p
 JOIN public.categories cat ON cat.id=p.category_id AND cat.is_active=TRUE
 LEFT JOIN public.categories parent ON parent.id=cat.parent_id
 LEFT JOIN public.product_prices pp ON pp.product_id=p.id
 LEFT JOIN public.inventory inv ON inv.product_id=p.id
 LEFT JOIN LATERAL (
   SELECT a.id,a.name_vi,a.name_en,a.status,a.starts_at,a.ends_at,a.promotion_type
   FROM public.promotions a
   WHERE EXISTS (
     SELECT 1 FROM public.promotion_products relation
     WHERE relation.product_id=p.id AND relation.promotion_id=a.id)
     AND a.status='PUBLISHED' AND a.starts_at<=CURRENT_TIMESTAMP AND a.ends_at>CURRENT_TIMESTAMP
   ORDER BY CASE WHEN a.promotion_type='FLASH_SALE' THEN 0 ELSE 1 END,
            a.ends_at ASC,a.id ASC LIMIT 1
 ) promo ON TRUE
 LEFT JOIN LATERAL (
  SELECT ROUND(AVG(r.rating),2) average_rating,COUNT(*) review_count
  FROM public.product_reviews r WHERE r.product_id=p.id AND r.deleted_at IS NULL
 ) ratings ON TRUE
 """;
    private static final String PUBLISHED_FILTER="""
 WHERE p.status='PUBLISHED' AND p.unit_code<>'COMBO'
 AND (cat.parent_id IS NULL OR parent.is_active=TRUE)
 """;
    private HomePageProductResponse mapProduct(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new HomePageProductResponse(
            rs.getObject("product_id",UUID.class),rs.getString("sku"),rs.getString("name_vi"),
            rs.getString("name_en"),rs.getString("image_url"),rs.getString("unit_code"),
            rs.getBigDecimal("list_price"),rs.getBigDecimal("sale_price"),rs.getBigDecimal("available_quantity"),
            rs.getObject("promotion_id",UUID.class),rs.getString("promotion_name_vi"),
            instant(rs.getTimestamp("promotion_ends_at")),rs.getObject("category_id",UUID.class),
            rs.getString("category_name_vi"),rs.getString("category_name_en"),
            rs.getString("package_spec_vi"),rs.getString("package_spec_en"),
            rs.getBigDecimal("min_order_quantity"),rs.getBigDecimal("quantity_step"),
            rs.getBigDecimal("vat_rate"),rs.getBigDecimal("average_rating"),rs.getLong("review_count")
        );
    }
    public List<HomePageProductResponse> standardProducts(int limit) {
        // Featured fallback includes out-of-stock products; inventory remains accurate in the response.
        return jdbc.query(PRODUCT_SELECT+PUBLISHED_FILTER+" AND pp.list_price IS NOT NULL ORDER BY p.created_at DESC,p.id ASC LIMIT ?",
            (rs,i)->mapProduct(rs),limit);
    }

    // Only actual completed orders from the last 30 days are counted. No mock/fallback sales.
    // Tie-break equal quantities by net merchandise revenue (after coupon, excluding VAT).
    public List<HomePageProductResponse> bestSellingProducts(int limit) {
        return jdbc.query(PRODUCT_SELECT + """
          JOIN (
             SELECT oi.product_id,
                    SUM(oi.quantity) AS sold_quantity,
                    SUM(GREATEST(oi.quantity * oi.sale_unit_price
                        - COALESCE(oi.coupon_discount_amount, 0), 0)) AS sales_revenue
             FROM public.order_items oi
             JOIN public.orders o ON o.id = oi.order_id
             WHERE o.status = 'COMPLETED'
               AND o.completed_at >= CURRENT_TIMESTAMP - INTERVAL '30 days'
               AND o.completed_at <= CURRENT_TIMESTAMP
             GROUP BY oi.product_id
          ) sales ON sales.product_id = p.id
          """ + PUBLISHED_FILTER + """
          AND pp.list_price IS NOT NULL
          AND COALESCE(inv.available_quantity, 0) >= p.min_order_quantity
          ORDER BY sales.sold_quantity DESC, sales.sales_revenue DESC, p.id ASC
          LIMIT ?
          """, (rs, i) -> mapProduct(rs), limit);
    }

    // Actual active promotion discount / list price, ranked by percentage descending.
    public List<HomePageProductResponse> deepDiscountProducts(int limit) {
        return jdbc.query(PRODUCT_SELECT + PUBLISHED_FILTER + """
          AND pp.list_price > 0 AND pp.sale_price IS NOT NULL
          AND pp.sale_price >= 0 AND pp.sale_price < pp.list_price
          AND promo.status = 'PUBLISHED' AND promo.starts_at <= CURRENT_TIMESTAMP
          AND promo.ends_at > CURRENT_TIMESTAMP AND COALESCE(inv.available_quantity, 0) > 0
          ORDER BY (pp.list_price - pp.sale_price) / pp.list_price DESC, p.id ASC LIMIT ?
          """, (rs, i) -> mapProduct(rs), limit);
    }

    // Require real, non-deleted reviews; show highly rated products even if sold out.
    public List<HomePageProductResponse> topRatedProducts(int limit) {
        return jdbc.query(PRODUCT_SELECT + PUBLISHED_FILTER + """
          AND ratings.review_count > 0 AND pp.list_price IS NOT NULL
          ORDER BY ratings.average_rating DESC, ratings.review_count DESC, p.id ASC LIMIT ?
          """, (rs, i) -> mapProduct(rs), limit);
    }

    // A flash deal is a genuine short-term, PUBLISHED promotion linked to the product.
    // No hard-coded minimum discount: smaller real discounts are allowed on a new store.
    // Only products with enough stock for the minimum order can be purchased.
    public List<HomePageProductResponse> flashSaleProducts(int limit) {
        return jdbc.query(PRODUCT_SELECT + PUBLISHED_FILTER + """
          AND promo.status = 'PUBLISHED'
          AND promo.starts_at <= CURRENT_TIMESTAMP AND promo.ends_at > CURRENT_TIMESTAMP
          AND promo.promotion_type = 'FLASH_SALE'
          AND promo.ends_at > promo.starts_at
          AND (promo.ends_at - promo.starts_at) <= INTERVAL '72 hours'
          AND pp.list_price > 0 AND pp.sale_price IS NOT NULL
          AND pp.sale_price >= 0 AND pp.sale_price < pp.list_price
          AND COALESCE(inv.available_quantity, 0) >= p.min_order_quantity
          ORDER BY promo.ends_at ASC,
                   (pp.list_price - pp.sale_price) / pp.list_price DESC, p.id ASC
          LIMIT ?
          """, (rs, i) -> mapProduct(rs), limit);
    }

    // A3: advisory sellable combo count is bounded by least available ingredient.
    public List<HomePageComboResponse> combos(int limit) {
        return combos(limit,0L);
    }

    public List<HomePageComboResponse> combos(int limit,long offset) {
        return combos(limit,offset,null);
    }

    public List<HomePageComboResponse> combos(int limit,long offset,UUID id) {
        String sql="""
  SELECT p.id product_id,p.sku,p.name_vi,p.name_en,p.image_url,c.serving_count,
   pp.list_price,
   (SELECT CASE WHEN COUNT(*) > 0 AND COUNT(ip.list_price)=COUNT(*)
           THEN SUM(ci.quantity*ip.list_price) END
    FROM public.combo_items ci LEFT JOIN public.product_prices ip ON ip.product_id=ci.product_id
    WHERE ci.combo_id=p.id) separate_list_price,
   CASE WHEN promo.status='PUBLISHED' AND promo.starts_at<=CURRENT_TIMESTAMP
          AND promo.ends_at>CURRENT_TIMESTAMP AND pp.sale_price>=0
          AND pp.sale_price<pp.list_price THEN pp.sale_price ELSE NULL END sale_price,
   p.min_order_quantity,p.quantity_step,p.vat_rate,ratings.average_rating,ratings.review_count,
   COALESCE((SELECT a.available_quantity FROM checkout_stock.availability a WHERE a.product_id=p.id),0) available_quantity
  FROM public.combos c
  JOIN public.products p ON p.id=c.product_id
  LEFT JOIN public.product_prices pp ON pp.product_id=p.id
  LEFT JOIN LATERAL (
   SELECT a.id,a.name_vi,a.name_en,a.status,a.starts_at,a.ends_at,a.promotion_type
   FROM public.promotions a
   WHERE EXISTS (
     SELECT 1 FROM public.promotion_products relation
     WHERE relation.product_id=p.id AND relation.promotion_id=a.id)
     AND a.status='PUBLISHED' AND a.starts_at<=CURRENT_TIMESTAMP AND a.ends_at>CURRENT_TIMESTAMP
   ORDER BY CASE WHEN a.promotion_type='FLASH_SALE' THEN 0 ELSE 1 END,
            a.ends_at ASC,a.id ASC LIMIT 1
 ) promo ON TRUE
  LEFT JOIN LATERAL (
   SELECT ROUND(AVG(r.rating),2) average_rating,COUNT(*) review_count
   FROM public.product_reviews r WHERE r.product_id=p.id AND r.deleted_at IS NULL
  ) ratings ON TRUE
  WHERE p.status='PUBLISHED' AND p.unit_code='COMBO'
   AND EXISTS(SELECT 1 FROM public.combo_items ci WHERE ci.combo_id=p.id)
   AND NOT EXISTS(SELECT 1 FROM public.combo_items ci
     JOIN public.products ingredient ON ingredient.id=ci.product_id
     WHERE ci.combo_id=p.id AND (ingredient.status<>'PUBLISHED' OR ingredient.unit_code='COMBO'))
   AND (CAST(? AS uuid) IS NULL OR p.id=CAST(? AS uuid))
  ORDER BY p.created_at DESC,p.id ASC LIMIT ? OFFSET ?
  """;
        List<HomePageComboResponse> combos=jdbc.query(sql,(rs,i)->new HomePageComboResponse(
            rs.getObject("product_id",UUID.class),rs.getString("sku"),rs.getString("name_vi"),
            rs.getString("name_en"),rs.getString("image_url"),(Integer)rs.getObject("serving_count"),
            rs.getBigDecimal("list_price"),rs.getBigDecimal("sale_price"),rs.getBigDecimal("available_quantity"),
            List.of(),rs.getBigDecimal("min_order_quantity"),rs.getBigDecimal("quantity_step"),
            rs.getBigDecimal("vat_rate"),rs.getBigDecimal("average_rating"),rs.getLong("review_count"),
            rs.getBigDecimal("separate_list_price")
        ),id,id,limit,offset);
        if(combos.isEmpty()) return combos;
        List<UUID> ids=combos.stream().map(HomePageComboResponse::productId).toList();
        String marks=String.join(",",Collections.nCopies(ids.size(),"?"));
        String itemSql="SELECT ci.combo_id,ci.product_id,p.sku,p.name_vi,p.name_en,p.unit_code,ci.quantity,p.image_url "
            +" FROM public.combo_items ci JOIN public.products p ON p.id=ci.product_id "
            +" WHERE ci.combo_id IN ("+marks+") ORDER BY ci.combo_id,p.name_vi,p.id";
        List<HomePageComboItemResponse> rows=jdbc.query(itemSql,(rs, i)->new HomePageComboItemResponse(
            rs.getObject("combo_id",UUID.class),rs.getObject("product_id",UUID.class),
            rs.getString("sku"),rs.getString("name_vi"),rs.getString("name_en"),
            rs.getString("unit_code"),rs.getBigDecimal("quantity"),rs.getString("image_url")
        ),ids.toArray());
        Map<UUID,List<HomePageComboItemResponse>> grouped=new HashMap<>();
        for(var item:rows) grouped.computeIfAbsent(item.comboId(),k->new ArrayList<>()).add(item);
        return combos.stream().map(c->new HomePageComboResponse(c.productId(),c.sku(),c.nameVi(),
            c.nameEn(),c.imageUrl(),c.servingCount(),c.listPrice(),c.salePrice(),c.availableQuantity(),
            List.copyOf(grouped.getOrDefault(c.productId(),List.of())),c.minOrderQuantity(),
            c.quantityStep(),c.vatRate(),c.averageRating(),c.reviewCount(),c.separateListPrice())).toList();
    }

    public List<HomePagePromotionResponse> activePromotions(int limit) {
        return jdbc.query("""
   SELECT id,name_vi,name_en,content_vi,content_en,starts_at,ends_at
   FROM public.promotions WHERE status='PUBLISHED'
    AND starts_at<=CURRENT_TIMESTAMP AND ends_at>CURRENT_TIMESTAMP
   ORDER BY ends_at ASC,id ASC LIMIT ?
   """,(rs,i)->new HomePagePromotionResponse(rs.getObject("id",UUID.class),
            rs.getString("name_vi"),rs.getString("name_en"),rs.getString("content_vi"),
            rs.getString("content_en"),instant(rs.getTimestamp("starts_at")),instant(rs.getTimestamp("ends_at"))),limit);
    }

    /**
     * The promotions table (V21) stores homepage campaign images directly.
     * Active published promotions are returned even if image_url is currently empty,
     * so the API still exposes text advertising and the data gap is visible.
     */
    public List<HomePageBannerResponse> banners(int limit) {
        return jdbc.query("""
   SELECT id,name_vi,name_en,content_vi,content_en,image_url
   FROM public.promotions
   WHERE status='PUBLISHED'
     AND starts_at<=CURRENT_TIMESTAMP
     AND ends_at>CURRENT_TIMESTAMP
   ORDER BY (NULLIF(BTRIM(image_url),'') IS NULL) ASC,starts_at DESC,created_at DESC,id ASC
   LIMIT ?
   """,(rs,i)->new HomePageBannerResponse(
            rs.getObject("id",UUID.class),
            rs.getString("name_vi"),rs.getString("name_en"),
            rs.getString("content_vi"),rs.getString("content_en"),
            rs.getString("image_url"),null,i),limit);
    }

    private record Node(UUID id,UUID parentId,String code,String vi,String en,String dvi,String den,String svg,int order) {}

    // A5: counts include active descendant categories, and only PUBLISHED products.
    public List<HomePageCategoryResponse> categories() {
        List<Node> nodes=jdbc.query("""
   SELECT id,parent_id,code,name_vi,name_en,description_vi,description_en,image_svg,sort_order
   FROM public.categories WHERE is_active=TRUE ORDER BY sort_order ASC,name_vi ASC,id ASC
   """,(rs,i)->new Node(rs.getObject("id",UUID.class),rs.getObject("parent_id",UUID.class),
            rs.getString("code"),rs.getString("name_vi"),rs.getString("name_en"),
            rs.getString("description_vi"),rs.getString("description_en"),
            rs.getString("image_svg"),rs.getInt("sort_order")));
        Map<UUID,Long> counts=new HashMap<>();
        jdbc.query("""
   WITH RECURSIVE descendants AS (
     SELECT id AS root_id,id AS child_id,1 AS depth FROM public.categories WHERE is_active=TRUE
     UNION ALL
     SELECT d.root_id,c.id,d.depth+1 FROM descendants d
       JOIN public.categories c ON c.parent_id=d.child_id AND c.is_active=TRUE
     WHERE d.depth<16
   )
   SELECT d.root_id,COUNT(p.id) product_count FROM descendants d
   LEFT JOIN public.products p ON p.category_id=d.child_id AND p.status='PUBLISHED'
   GROUP BY d.root_id
   """,(org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            counts.put(rs.getObject("root_id",UUID.class),rs.getLong("product_count"));
        });
        Map<UUID,List<Node>> children=new HashMap<>();
        for(Node n:nodes) {if(n.parentId()!=null)
            children.computeIfAbsent(n.parentId(),k->new ArrayList<>()).add(n);}
        List<HomePageCategoryResponse> roots=new ArrayList<>();
        for(Node n:nodes) if(n.parentId()==null)
            roots.add(categoryTree(n,children,counts,new HashSet<>()));
        return roots;
    }
    private HomePageCategoryResponse categoryTree(Node node, Map<UUID,List<Node>> children,
                                                  Map<UUID,Long> counts, Set<UUID> ancestors) {
        if (!ancestors.add(node.id())) return categoryDto(node, counts, List.of());
        List<HomePageCategoryResponse> nested=new ArrayList<>();
        for(Node child:children.getOrDefault(node.id(),List.of())) {
            if (!ancestors.contains(child.id())) nested.add(categoryTree(child,children,counts,ancestors));
        }
        ancestors.remove(node.id());
        return categoryDto(node,counts,nested);
    }
    private HomePageCategoryResponse categoryDto(Node node, Map<UUID,Long> counts,
                                                 List<HomePageCategoryResponse> children) {
        return new HomePageCategoryResponse(node.id(),node.parentId(),node.code(),node.vi(),
            node.en(),node.dvi(),node.den(),node.svg(),node.order(),
            counts.getOrDefault(node.id(),0L),List.copyOf(children));
    }
}
