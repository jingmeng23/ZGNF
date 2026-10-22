package com.gas.ecommerce.modules.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.inventory.entity.Inventory;
import com.gas.ecommerce.modules.inventory.mapper.InventoryMapper;
import com.gas.ecommerce.modules.product.dto.ProductQueryRequest;
import com.gas.ecommerce.modules.product.entity.Category;
import com.gas.ecommerce.modules.product.entity.Product;
import com.gas.ecommerce.modules.product.mapper.CategoryMapper;
import com.gas.ecommerce.modules.product.mapper.ProductMapper;
import com.gas.ecommerce.modules.product.vo.CategoryVO;
import com.gas.ecommerce.modules.product.vo.ProductVO;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ProductBrowseService {
    private final ProductMapper products;
    private final CategoryMapper categories;
    private final InventoryMapper inventory;

    public ProductBrowseService(ProductMapper products, CategoryMapper categories, InventoryMapper inventory) {
        this.products = products; this.categories = categories; this.inventory = inventory;
    }

    public List<CategoryVO> categories() {
        List<Category> active = categories.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getStatus, "active").orderByAsc(Category::getSort, Category::getId));
        Set<Long> ids = active.stream().map(Category::getId).collect(Collectors.toSet());
        return active.stream().filter(c -> visible(c, ids, active)).map(CategoryVO::from).toList();
    }

    public PageResult<ProductVO> products(ProductQueryRequest request) {
        String sort = request.getSort();
        if (!Set.of("createTime", "price", "sales", "id").contains(sort))
            throw new com.gas.ecommerce.common.exception.BusinessException("不支持的排序字段");
        List<CategoryVO> visible = categories();
        Set<Long> categoryIds = visible.stream().map(CategoryVO::id).collect(Collectors.toSet());
        if (request.getCategoryId() != null) {
            if (!categoryIds.contains(request.getCategoryId())) return empty(request);
            Map<Long, List<Long>> children = visible.stream().collect(Collectors.groupingBy(
                    CategoryVO::parentId, Collectors.mapping(CategoryVO::id, Collectors.toList())));
            categoryIds = new HashSet<>();
            Deque<Long> pending = new ArrayDeque<>(); pending.add(request.getCategoryId());
            while (!pending.isEmpty()) {
                Long id = pending.removeFirst();
                if (categoryIds.add(id)) pending.addAll(children.getOrDefault(id, List.of()));
            }
        }
        if (categoryIds.isEmpty()) return empty(request);
        var query = Wrappers.<Product>lambdaQuery().eq(Product::getStatus, "on_sale")
                .in(Product::getCategoryId, categoryIds)
                .like(request.getKeyword() != null && !request.getKeyword().isBlank(),
                        Product::getName, request.getKeyword() == null ? null : request.getKeyword().trim());
        boolean asc = "asc".equals(request.getOrder());
        switch (sort) {
            case "price" -> query.orderBy(true, asc, Product::getPrice);
            case "sales" -> query.orderBy(true, asc, Product::getSales);
            case "id" -> query.orderBy(true, asc, Product::getId);
            default -> query.orderBy(true, asc, Product::getCreateTime);
        }
        if (!"id".equals(sort)) query.orderByDesc(Product::getId);
        Page<Product> page = products.selectPage(new Page<>(request.getPage(), request.getSize()), query);
        return new PageResult<>(views(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    public Optional<ProductVO> product(Long id) {
        Product product = products.selectById(id);
        if (product == null || !"on_sale".equals(product.getStatus())) return Optional.empty();
        boolean visibleCategory = categories().stream().anyMatch(c -> c.id().equals(product.getCategoryId()));
        if (!visibleCategory) return Optional.empty();
        return Optional.of(views(List.of(product)).getFirst());
    }

    private PageResult<ProductVO> empty(ProductQueryRequest request) {
        return new PageResult<>(List.of(), 0, request.getPage(), request.getSize(), 0);
    }

    private boolean visible(Category category, Set<Long> ids, List<Category> active) {
        Set<Long> seen = new HashSet<>();
        Long parent = category.getParentId();
        while (parent != 0) {
            if (!ids.contains(parent) || !seen.add(parent)) return false;
            Long current = parent;
            parent = active.stream().filter(c -> c.getId().equals(current)).findFirst()
                    .map(Category::getParentId).orElse(-1L);
        }
        return true;
    }

    private List<ProductVO> views(List<Product> list) {
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(Product::getId).toList();
        Map<Long, Long> stocks = new HashMap<>();
        for (Inventory item : inventory.selectList(Wrappers.<Inventory>lambdaQuery().in(Inventory::getProductId, ids))) {
            long available = Math.max(0L, (long) item.getQuantity() - item.getLockedQuantity());
            stocks.merge(item.getProductId(), available, Long::sum);
        }
        return list.stream().map(p -> new ProductVO(p.getId(), p.getCategoryId(), p.getName(), p.getSubtitle(),
                p.getCoverUrl(), p.getPrice(), p.getOriginalPrice(), p.getSales(),
                stocks.getOrDefault(p.getId(), 0L))).toList();
    }
}
