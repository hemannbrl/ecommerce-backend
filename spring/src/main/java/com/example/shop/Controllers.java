package com.example.shop;

import java.util.List;
import java.util.Map;

import com.example.shop.domain.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health")
class HealthController {
    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }
}

@RestController
@Tag(name = "Auth")
class AuthController {
    private final AuthService auth;
    AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    Dto.AuthResponse register(@Valid @RequestBody Dto.RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/auth/login")
    Dto.AuthResponse login(@Valid @RequestBody Dto.LoginRequest req) {
        return auth.login(req);
    }

    @GetMapping("/auth/me")
    Dto.UserDto me(@AuthenticationPrincipal User user) {
        return AuthService.toDto(user);
    }
}

@RestController
@Tag(name = "Catalog")
class CatalogController {
    private final CatalogService catalog;
    CatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/products")
    Dto.ProductPage list(@RequestParam(required = false) String search,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String sort,
                         @RequestParam(defaultValue = "1") int page,
                         @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return catalog.list(search, category, sort, Math.max(1, page), Math.min(100, Math.max(1, pageSize)));
    }

    @GetMapping("/products/{id}")
    Dto.ProductDto get(@PathVariable Long id) {
        return catalog.get(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    Dto.ProductDto create(@Valid @RequestBody Dto.ProductWriteRequest req) {
        return catalog.create(req);
    }

    @PatchMapping("/products/{id}")
    Dto.ProductDto update(@PathVariable Long id, @RequestBody Dto.ProductWriteRequest req) {
        return catalog.update(id, req);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        catalog.delete(id);
    }

    @GetMapping("/categories")
    List<Dto.CategoryDto> categories() {
        return catalog.categories();
    }
}

@RestController
@Tag(name = "Cart")
class CartController {
    private final CartService cart;
    CartController(CartService cart) { this.cart = cart; }

    @GetMapping("/cart")
    Dto.CartDto view(@AuthenticationPrincipal User user) {
        return cart.view(user.getId());
    }

    @PostMapping("/cart/items")
    Dto.CartDto add(@AuthenticationPrincipal User user, @Valid @RequestBody Dto.CartItemWriteRequest req) {
        return cart.addItem(user.getId(), req);
    }

    @PatchMapping("/cart/items/{id}")
    Dto.CartDto setQuantity(@AuthenticationPrincipal User user, @PathVariable Long id,
                            @Valid @RequestBody Dto.QuantityRequest req) {
        return cart.setQuantity(user.getId(), id, req.quantity());
    }

    @DeleteMapping("/cart/items/{id}")
    Dto.CartDto remove(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return cart.removeItem(user.getId(), id);
    }
}

@RestController
@Tag(name = "Orders")
class OrderController {
    private final OrderService orders;
    OrderController(OrderService orders) { this.orders = orders; }

    @GetMapping("/orders")
    List<Dto.OrderDto> list(@AuthenticationPrincipal User user) {
        return orders.ordersFor(user.getId());
    }

    @PostMapping("/orders")
    ResponseEntity<Dto.OrderDto> checkout(@AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orders.checkout(user.getId()));
    }

    @GetMapping("/orders/{id}")
    Dto.OrderDto get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return orders.getForUser(id, user.getId(), user.isAdmin());
    }

    @PatchMapping("/orders/{id}/status")
    Dto.OrderDto updateStatus(@PathVariable Long id, @Valid @RequestBody Dto.OrderStatusRequest req) {
        return orders.updateStatus(id, req.status());
    }
}
