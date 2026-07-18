package com.example.shop;

import java.util.List;
import java.util.Optional;

import com.example.shop.domain.Cart;
import com.example.shop.domain.CartItem;
import com.example.shop.domain.Category;
import com.example.shop.domain.Inventory;
import com.example.shop.domain.OrderItem;
import com.example.shop.domain.OrderRow;
import com.example.shop.domain.Product;
import com.example.shop.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}

interface CategoryRepository extends JpaRepository<Category, Long> {
}

interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("""
        select p from Product p left join p.category c
        where p.active = true
          and (:search is null or lower(p.name) like lower(concat('%', :search, '%'))
                              or lower(p.description) like lower(concat('%', :search, '%')))
          and (:categorySlug is null or c.slug = :categorySlug)
        """)
    Page<Product> search(@Param("search") String search,
                         @Param("categorySlug") String categorySlug,
                         Pageable pageable);

    boolean existsBySlug(String slug);
}

interface InventoryRepository extends JpaRepository<Inventory, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.productId = :productId")
    Optional<Inventory> lockByProductId(@Param("productId") Long productId);
}

interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);
}

interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartIdOrderById(Long cartId);
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);
    void deleteByCartId(Long cartId);
}

interface OrderRepository extends JpaRepository<OrderRow, Long> {
    List<OrderRow> findByUserIdOrderByIdDesc(Long userId);
}

interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderIdOrderById(Long orderId);
}
