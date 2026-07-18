package com.example.shop;

import java.util.List;
import java.util.Locale;

import com.example.shop.domain.Category;
import com.example.shop.domain.Inventory;
import com.example.shop.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
class CatalogService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final InventoryRepository inventory;

    CatalogService(ProductRepository products, CategoryRepository categories,
                   InventoryRepository inventory) {
        this.products = products;
        this.categories = categories;
        this.inventory = inventory;
    }

    private Sort sortFor(String sort) {
        return switch (sort == null ? "" : sort) {
            case "price" -> Sort.by("price").ascending();
            case "-price" -> Sort.by("price").descending();
            case "name" -> Sort.by("name").ascending();
            case "-name" -> Sort.by("name").descending();
            case "newest" -> Sort.by("createdAt").descending();
            default -> Sort.by("id").ascending();
        };
    }

    Dto.ProductPage list(String search, String category, String sort, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, sortFor(sort));
        Page<Product> result = products.search(
                search == null || search.isBlank() ? null : search,
                category == null || category.isBlank() ? null : category,
                pageable);
        List<Dto.ProductDto> dtos = result.getContent().stream().map(this::toDto).toList();
        return new Dto.ProductPage(dtos, page, pageSize, result.getTotalElements());
    }

    Dto.ProductDto get(Long id) {
        Product p = products.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        return toDto(p);
    }

    Dto.ProductDto create(Dto.ProductWriteRequest req) {
        Product p = new Product();
        p.setName(req.name());
        p.setSlug(uniqueSlug(req.name()));
        p.setDescription(req.description() == null ? "" : req.description());
        p.setPrice(req.price());
        p.setActive(req.isActive() == null || req.isActive());
        if (req.categoryId() != null) {
            p.setCategory(categories.findById(req.categoryId()).orElse(null));
        }
        products.save(p);

        Inventory inv = new Inventory();
        inv.setProductId(p.getId());
        inv.setQuantity(req.stock() == null ? 0 : req.stock());
        inventory.save(inv);
        return toDto(p);
    }

    Dto.ProductDto update(Long id, Dto.ProductWriteRequest req) {
        Product p = products.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        if (req.name() != null) p.setName(req.name());
        if (req.description() != null) p.setDescription(req.description());
        if (req.price() != null) p.setPrice(req.price());
        if (req.isActive() != null) p.setActive(req.isActive());
        if (req.categoryId() != null) p.setCategory(categories.findById(req.categoryId()).orElse(null));
        products.save(p);
        if (req.stock() != null) {
            Inventory inv = inventory.findById(id).orElseGet(() -> {
                Inventory n = new Inventory();
                n.setProductId(id);
                return n;
            });
            inv.setQuantity(req.stock());
            inventory.save(inv);
        }
        return toDto(p);
    }

    void delete(Long id) {
        if (!products.existsById(id)) throw new NotFoundException("Product not found");
        products.deleteById(id);
    }

    List<Dto.CategoryDto> categories() {
        return categories.findAll(Sort.by("name")).stream()
                .map(c -> new Dto.CategoryDto(c.getId(), c.getName(), c.getSlug()))
                .toList();
    }

    Dto.ProductDto toDto(Product p) {
        int stock = inventory.findById(p.getId()).map(Inventory::getQuantity).orElse(0);
        return new Dto.ProductDto(p.getId(), p.getName(), p.getSlug(), p.getDescription(),
                p.getPrice(), p.getCategoryId(), p.isActive(), stock);
    }

    private String uniqueSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "product";
        String slug = base;
        int n = 1;
        while (products.existsBySlug(slug)) {
            n++;
            slug = base + "-" + n;
        }
        return slug;
    }
}
