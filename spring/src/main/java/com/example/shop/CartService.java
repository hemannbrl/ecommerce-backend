package com.example.shop;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.example.shop.domain.Cart;
import com.example.shop.domain.CartItem;
import com.example.shop.domain.Product;
import org.springframework.stereotype.Service;

@Service
class CartService {

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;

    CartService(CartRepository carts, CartItemRepository items, ProductRepository products) {
        this.carts = carts;
        this.items = items;
        this.products = products;
    }

    Cart getOrCreate(Long userId) {
        return carts.findByUserId(userId).orElseGet(() -> {
            Cart c = new Cart();
            c.setUserId(userId);
            return carts.save(c);
        });
    }

    Dto.CartDto view(Long userId) {
        return toDto(getOrCreate(userId));
    }

    Dto.CartDto addItem(Long userId, Dto.CartItemWriteRequest req) {
        Product product = products.findById(req.productId())
                .orElseThrow(() -> new NotFoundException("Product not found"));
        Cart cart = getOrCreate(userId);
        CartItem item = items.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElseGet(() -> {
                    CartItem c = new CartItem();
                    c.setCartId(cart.getId());
                    c.setProductId(product.getId());
                    c.setQuantity(0);
                    return c;
                });
        item.setQuantity(item.getQuantity() + req.quantity());
        items.save(item);
        return toDto(cart);
    }

    Dto.CartDto setQuantity(Long userId, Long itemId, int quantity) {
        CartItem item = ownedItem(userId, itemId);
        item.setQuantity(quantity);
        items.save(item);
        return view(userId);
    }

    Dto.CartDto removeItem(Long userId, Long itemId) {
        CartItem item = ownedItem(userId, itemId);
        items.delete(item);
        return view(userId);
    }

    private CartItem ownedItem(Long userId, Long itemId) {
        Cart cart = getOrCreate(userId);
        CartItem item = items.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Cart item not found"));
        if (!item.getCartId().equals(cart.getId())) {
            throw new NotFoundException("Cart item not found");
        }
        return item;
    }

    Dto.CartDto toDto(Cart cart) {
        List<Dto.CartItemDto> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items.findByCartIdOrderById(cart.getId())) {
            Product p = products.findById(item.getProductId()).orElseThrow();
            BigDecimal lineTotal = p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(lineTotal);
            lines.add(new Dto.CartItemDto(item.getId(), p.getId(), p.getName(),
                    p.getPrice(), item.getQuantity(), lineTotal));
        }
        return new Dto.CartDto(cart.getId(), lines, total);
    }
}
