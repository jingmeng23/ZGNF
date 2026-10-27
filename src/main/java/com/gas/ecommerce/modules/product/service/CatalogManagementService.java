package com.gas.ecommerce.modules.product.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.product.dto.*;
import com.gas.ecommerce.modules.product.entity.*;
import com.gas.ecommerce.modules.product.mapper.*;
import com.gas.ecommerce.modules.product.vo.*;
import com.gas.ecommerce.modules.inventory.entity.Inventory;
import com.gas.ecommerce.modules.inventory.mapper.InventoryMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogManagementService {
    private final ProductMapper products;
    private final CategoryMapper categories;
    private final BrandMapper brands;
    private final InventoryMapper inventory;
    private final CatalogLockMapper locks; private final org.springframework.context.ApplicationEventPublisher events;

    public CatalogManagementService(ProductMapper products, CategoryMapper categories, BrandMapper brands,
            InventoryMapper inventory, CatalogLockMapper locks, org.springframework.context.ApplicationEventPublisher events) { this.events = events;
        this.products = products; this.categories = categories; this.brands = brands;
        this.inventory = inventory; this.locks = locks;
    }

    public PageResult<AdminProductVO> products(AdminProductQuery request) {
        QueryWrapper<Product> query = new QueryWrapper<>();
        query.eq(request.getCategoryId() != null, "category_id", request.getCategoryId());
        query.eq(request.getStatus() != null, "status", request.getStatus());
        query.like(request.getKeyword() != null && !request.getKeyword().isBlank(), "name", request.getKeyword());
        if (request.getFilter() != null && !request.getFilter().isBlank() && !"全部分类".equals(request.getFilter())) {
            List<Long> ids = categories.selectList(Wrappers.<Category>lambdaQuery().eq(Category::getName, request.getFilter()))
                    .stream().map(Category::getId).toList();
            if (ids.isEmpty()) return new PageResult<>(List.of(), 0, request.getPage(), request.getSize(), 0);
            query.in("category_id", ids);
        }
        query.orderBy(true, "asc".equals(request.getOrder()), request.resolveSort(Map.of(
                "createTime", "create_time", "price", "price", "sales", "sales", "id", "id"))).orderByDesc("id");
        Page<Product> page = products.selectPage(new Page<>(request.getPage(), request.getSize()), query);
        return new PageResult<>(views(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    public AdminProductVO product(Long id) { return views(List.of(requireProduct(id))).getFirst(); }

    @Transactional
    public AdminProductVO create(ProductCreateRequest r) {
        lock(); validateReferences(r.categoryId(), r.brandId(), r.price(), r.originalPrice(), false);
        Product p = new Product(); p.setCategoryId(r.categoryId()); p.setBrandId(r.brandId());
        p.setName(r.name().trim()); p.setSubtitle(r.subtitle()); p.setCoverUrl(r.coverUrl());
        p.setDescription(r.description());
        p.setDetailImages(r.detailImages() == null ? null : String.join("\n", r.detailImages()));
        p.setPrice(r.price()); p.setOriginalPrice(r.originalPrice()); p.setStatus("draft"); p.setSales(0L); p.setIsDeleted(0);
        products.insert(p); events.publishEvent(new CatalogReadService.ProductDetailChanged(p.getId())); return product(p.getId());
    }

    @Transactional
    public AdminProductVO update(Long id, ProductUpdateRequest r) {
        lock(); Product p = requireProduct(id);
        validateReferences(r.categoryId(), r.brandId(), r.price(), r.originalPrice(), Set.of("on_sale", "presale", "scheduled", "sold_out").contains(p.getStatus()));
        // 显式写入可空字段，允许 PUT 清除旧品牌、封面和原价。
        products.update(null, new UpdateWrapper<Product>().eq("id", id)
                .set("category_id", r.categoryId()).set("brand_id", r.brandId()).set("name", r.name().trim())
                .set("subtitle", r.subtitle()).set("cover_url", r.coverUrl()).set("price", r.price())
                .set("description", r.description()).set("detail_images", r.detailImages() == null ? null : String.join("\n", r.detailImages()))
                .set("original_price", r.originalPrice()).set("update_time", java.time.LocalDateTime.now()));
        events.publishEvent(new CatalogReadService.ProductDetailChanged(id));
        return product(id);
    }

    @Transactional
    public AdminProductVO status(Long id, String status) {
        lock(); Product p = requireProduct(id);
        if (Set.of("on_sale", "presale", "scheduled", "sold_out").contains(status)) validateReferences(p.getCategoryId(), p.getBrandId(), p.getPrice(), p.getOriginalPrice(), true);
        p.setStatus(status); products.updateById(p); events.publishEvent(new CatalogReadService.ProductDetailChanged(id)); return product(id);
    }

    @Transactional
    public void deleteProduct(Long id) {
        lock(); Product p = requireProduct(id);
        if (Set.of("on_sale", "presale", "scheduled", "sold_out").contains(p.getStatus())) throw new BusinessException("请先下架商品再删除");
        if (inventory.selectCount(Wrappers.<Inventory>lambdaQuery().eq(Inventory::getProductId,id)
                .and(w->w.ne(Inventory::getQuantity,0).or().ne(Inventory::getLockedQuantity,0)))>0)
            throw new BusinessException("商品仍有库存或订单预占，请先处理库存");
        products.deleteById(id); events.publishEvent(new CatalogReadService.ProductDetailChanged(id));
    }

    public List<CategoryVO> categories() {
        return categories.selectList(Wrappers.<Category>lambdaQuery().orderByAsc(Category::getSort, Category::getId))
                .stream().map(CategoryVO::from).toList();
    }
    public CategoryVO category(Long id) { return CategoryVO.from(requireCategory(id)); }

    @Transactional
    public CategoryVO createCategory(CategoryRequest r) {
        lock(); validateCategory(null, r);
        Category c = new Category(); c.setParentId(r.parentId()); c.setName(r.name().trim());
        c.setSort(r.sort()); c.setStatus(r.status()); c.setIsDeleted(0); categories.insert(c); events.publishEvent(new CategoryTreeService.CatalogChanged());
        return CategoryVO.from(c);
    }

    @Transactional
    public CategoryVO updateCategory(Long id, CategoryRequest r) {
        lock(); Category c = requireCategory(id); validateCategory(id, r);
        c.setParentId(r.parentId()); c.setName(r.name().trim()); c.setSort(r.sort()); c.setStatus(r.status());
        categories.updateById(c); events.publishEvent(new CategoryTreeService.CatalogChanged());
        return CategoryVO.from(c);
    }

    @Transactional
    public void deleteCategory(Long id) {
        lock(); requireCategory(id);
        if (categories.exists(Wrappers.<Category>lambdaQuery().eq(Category::getParentId, id)))
            throw new BusinessException("分类下存在子分类，不能删除");
        if (products.exists(Wrappers.<Product>lambdaQuery().eq(Product::getCategoryId, id)))
            throw new BusinessException("分类下存在商品，不能删除");
        categories.deleteById(id); events.publishEvent(new CategoryTreeService.CatalogChanged());
    }

    private void validateCategory(Long id, CategoryRequest r) {
        if (categories.exists(Wrappers.<Category>lambdaQuery().eq(Category::getParentId, r.parentId())
                .eq(Category::getName, r.name().trim()).ne(id != null, Category::getId, id)))
            throw new BusinessException("同一父分类下已存在该名称");
        Set<Long> visited = new HashSet<>();
        Long current = r.parentId();
        while (current != 0) {
            if (current.equals(id) || !visited.add(current)) throw new BusinessException("分类父子关系不能形成循环");
            Category parent = requireCategory(current);
            if ("active".equals(r.status()) && !"active".equals(parent.getStatus()))
                throw new BusinessException("启用分类的父分类必须已启用");
            current = parent.getParentId();
        }
        if (id != null && "inactive".equals(r.status())) {
            if (categories.exists(Wrappers.<Category>lambdaQuery().eq(Category::getParentId, id).eq(Category::getStatus, "active")))
                throw new BusinessException("请先停用子分类");
            if (products.exists(Wrappers.<Product>lambdaQuery().eq(Product::getCategoryId, id).in(Product::getStatus, Set.of("on_sale", "presale", "scheduled", "sold_out"))))
                throw new BusinessException("请先下架该分类下的商品");
        }
    }

    private void validateReferences(Long categoryId, Long brandId, BigDecimal price, BigDecimal original, boolean onSale) {
        Category category = requireCategory(categoryId);
        if (onSale) {
            Set<Long> seen = new HashSet<>();
            while (true) {
                if (!seen.add(category.getId()) || !"active".equals(category.getStatus()))
                    throw new BusinessException("商品分类及其父分类必须已启用");
                if (category.getParentId() == 0) break;
                category = requireCategory(category.getParentId());
            }
        }
        if (brandId != null) {
            Brand brand = brands.selectById(brandId);
            if (brand == null || (onSale && !"active".equals(brand.getStatus()))) throw new BusinessException("品牌不存在或未启用");
        }
        if (original != null && original.compareTo(price) < 0) throw new BusinessException("原价不能低于销售价");
    }

    private Product requireProduct(Long id) {
        Product p = products.selectById(id); if (p == null) throw new BusinessException("商品不存在"); return p;
    }
    private Category requireCategory(Long id) {
        Category c = categories.selectById(id); if (c == null) throw new BusinessException("分类不存在"); return c;
    }
    private void lock() { if (locks.lock() == null) throw new IllegalStateException("缺少商品分类事务锁，请执行数据库迁移"); }

    private List<AdminProductVO> views(List<Product> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> categoryIds = list.stream().map(Product::getCategoryId).collect(Collectors.toSet());
        Map<Long, String> names = categories.selectList(Wrappers.<Category>lambdaQuery().in(Category::getId, categoryIds))
                .stream().collect(Collectors.toMap(Category::getId, Category::getName));
        List<Long> productIds = list.stream().map(Product::getId).toList();
        Map<Long, Long> stocks = new HashMap<>();
        for (Inventory i : inventory.selectList(Wrappers.<Inventory>lambdaQuery().in(Inventory::getProductId, productIds)))
            stocks.merge(i.getProductId(), Math.max(0L, (long) i.getQuantity() - i.getLockedQuantity()), Long::sum);
        return list.stream().map(p -> new AdminProductVO(p.getId(), p.getCategoryId(), names.get(p.getCategoryId()),
                p.getBrandId(), p.getName(), p.getSubtitle(), p.getCoverUrl(), p.getPrice(), p.getOriginalPrice(),
                p.getStatus(), p.getSales(), stocks.getOrDefault(p.getId(), 0L), p.getCreateTime(), p.getUpdateTime(),
                p.getDescription(), p.getDetailImages() == null || p.getDetailImages().isBlank() ? List.of() : Arrays.asList(p.getDetailImages().split("\\n")))).toList();
    }
}
