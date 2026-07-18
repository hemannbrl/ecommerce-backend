package com.example.shop;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Request/response shapes for the API (see api/openapi.yaml). */
public final class Dto {
    private Dto() {}

    // --- auth ---
    public record RegisterRequest(@Email @NotBlank String email,
                                  @NotBlank @Size(min = 8) String password) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record UserDto(Long id, String email, String role) {}

    public record AuthResponse(String token, UserDto user) {}

    // --- catalog ---
    public record ProductDto(Long id, String name, String slug, String description,
                             BigDecimal price, Long categoryId, boolean isActive, int stock) {}

    public record ProductWriteRequest(@NotBlank String name, String description,
                                      @NotNull @PositiveOrZero BigDecimal price,
                                      Long categoryId, Boolean isActive,
                                      @PositiveOrZero Integer stock) {}

    public record ProductPage(List<ProductDto> results, int page, int pageSize, long total) {}

    public record CategoryDto(Long id, String name, String slug) {}

    // --- cart ---
    public record CartItemWriteRequest(@NotNull Long productId, @NotNull @Min(1) Integer quantity) {}

    public record QuantityRequest(@NotNull @Min(1) Integer quantity) {}

    public record CartItemDto(Long id, Long productId, String name,
                              BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}

    public record CartDto(Long id, List<CartItemDto> items, BigDecimal total) {}

    // --- orders ---
    public record OrderStatusRequest(@NotBlank String status) {}

    public record OrderItemDto(Long productId, String name, int quantity, BigDecimal unitPrice) {}

    public record OrderDto(Long id, String status, BigDecimal total,
                           OffsetDateTime createdAt, List<OrderItemDto> items) {}
}
