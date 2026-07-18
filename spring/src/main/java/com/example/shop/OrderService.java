package com.example.shop;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.example.shop.domain.Cart;
import com.example.shop.domain.CartItem;
import com.example.shop.domain.Inventory;
import com.example.shop.domain.OrderItem;
import com.example.shop.domain.OrderRow;
import com.example.shop.domain.Product;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderService {

    private final CartService cartService;
    private final CartItemRepository cartItems;
    private final InventoryRepository inventory;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;

    OrderService(CartService cartService, CartItemRepository cartItems,
                 InventoryRepository inventory, ProductRepository products,
                 OrderRepository orders, OrderItemRepository orderItems) {
        this.cartService = cartService;
        this.cartItems = cartItems;
        this.inventory = inventory;
        this.products = products;
        this.orders = orders;
        this.orderItems = orderItems;
    }

    /**
     * Checkout: turn the user's cart into an order, atomically. Locks each
     * product's stock row, decrements it (the DB CHECK blocks overselling),
     * snapshots unit prices, and clears the cart. Any failure rolls it all back.
     */
    @Transactional
    public Dto.OrderDto checkout(Long userId) {
        Cart cart = cartService.getOrCreate(userId);
        List<CartItem> lines = cartItems.findByCartIdOrderById(cart.getId());
        if (lines.isEmpty()) {
            throw new ConflictException("Cart is empty");
        }

        OrderRow order = new OrderRow();
        order.setUserId(userId);
        order.setStatus("pending");
        order.setTotal(BigDecimal.ZERO);
        orders.save(order);

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem line : lines) {
            Inventory stock = inventory.lockByProductId(line.getProductId())   // SELECT ... FOR UPDATE
                    .orElseThrow(() -> new ConflictException("No stock record"));
            Product product = products.findById(line.getProductId()).orElseThrow();

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(product.getId());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(product.getPrice());
            orderItems.save(item);

            stock.setQuantity(stock.getQuantity() - line.getQuantity());   // CHECK (quantity >= 0)
            try {
                inventory.saveAndFlush(stock);           // flush now so the CHECK fires here
            } catch (DataIntegrityViolationException e) {
                throw new ConflictException("Insufficient stock");
            }
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
        }

        order.setTotal(total);
        orders.save(order);
        cartItems.deleteByCartId(cart.getId());
        return toDto(order);
    }

    List<Dto.OrderDto> ordersFor(Long userId) {
        return orders.findByUserIdOrderByIdDesc(userId).stream().map(this::toDto).toList();
    }

    Dto.OrderDto getForUser(Long orderId, Long userId, boolean admin) {
        OrderRow order = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (!order.getUserId().equals(userId) && !admin) {
            throw new ForbiddenException("Not your order");
        }
        return toDto(order);
    }

    private static final List<String> STATUSES =
            List.of("pending", "paid", "shipped", "delivered", "cancelled");

    @Transactional
    Dto.OrderDto updateStatus(Long orderId, String status) {
        if (!STATUSES.contains(status)) {
            throw new BadRequestException("Invalid status");
        }
        OrderRow order = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        order.setStatus(status);
        orders.save(order);
        return toDto(order);
    }

    Dto.OrderDto toDto(OrderRow order) {
        List<Dto.OrderItemDto> items = new ArrayList<>();
        for (OrderItem it : orderItems.findByOrderIdOrderById(order.getId())) {
            String name = products.findById(it.getProductId()).map(Product::getName).orElse("");
            items.add(new Dto.OrderItemDto(it.getProductId(), name, it.getQuantity(), it.getUnitPrice()));
        }
        return new Dto.OrderDto(order.getId(), order.getStatus(), order.getTotal(),
                order.getCreatedAt(), items);
    }
}
