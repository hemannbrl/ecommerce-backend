package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;

import com.example.shop.domain.Cart;
import com.example.shop.domain.CartItem;
import com.example.shop.domain.Inventory;
import com.example.shop.domain.Product;
import com.example.shop.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The important test: two shoppers race for the last unit in stock. The
 * pessimistic row lock plus the CHECK constraint must guarantee exactly one
 * checkout succeeds and stock never goes negative.
 */
class CheckoutConcurrencyTest extends IntegrationTest {

    @Autowired OrderService orderService;
    @Autowired ProductRepository products;
    @Autowired InventoryRepository inventory;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;

    @Test
    void two_checkouts_cannot_oversell_the_last_unit() throws Exception {
        Product product = new Product();
        product.setName("LastOne");
        product.setSlug("lastone");
        product.setPrice(new BigDecimal("10.00"));
        product.setActive(true);
        products.save(product);

        Inventory stock = new Inventory();
        stock.setProductId(product.getId());
        stock.setQuantity(1);                 // ONE in stock
        inventory.save(stock);

        User[] buyers = new User[2];
        for (int i = 0; i < 2; i++) {
            User u = makeUser("race" + i + "@shop.test", "customer");
            Cart cart = new Cart();
            cart.setUserId(u.getId());
            carts.save(cart);
            CartItem item = new CartItem();
            item.setCartId(cart.getId());
            item.setProductId(product.getId());
            item.setQuantity(1);
            cartItems.save(item);
            buyers[i] = u;
        }

        var results = new ConcurrentHashMap<Long, String>();
        var barrier = new CyclicBarrier(2);
        Thread[] threads = new Thread[2];
        for (int i = 0; i < 2; i++) {
            User u = buyers[i];
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                    orderService.checkout(u.getId());
                    results.put(u.getId(), "ok");
                } catch (ConflictException e) {
                    results.put(u.getId(), "rejected");
                } catch (Exception e) {
                    results.put(u.getId(), "error:" + e.getClass().getSimpleName());
                }
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertThat(results.values().stream().sorted().toList()).containsExactly("ok", "rejected");
        assertThat(inventory.findById(product.getId()).orElseThrow().getQuantity()).isEqualTo(0);
    }
}
