package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class ApiIntegrationTest extends IntegrationTest {

    @Autowired TestRestTemplate rest;

    private HttpHeaders json(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return h;
    }

    private String token(String email) {
        var body = "{\"email\":\"%s\",\"password\":\"Password123!\"}".formatted(email);
        var resp = rest.postForEntity("/auth/login",
                new HttpEntity<>(body, json(null)), Map.class);
        return (String) resp.getBody().get("token");
    }

    @Test
    void register_login_me_flow() {
        var body = "{\"email\":\"new@shop.test\",\"password\":\"Password123!\"}";
        var reg = rest.postForEntity("/auth/register", new HttpEntity<>(body, json(null)), Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reg.getBody().get("token")).isNotNull();

        var me = rest.exchange("/auth/me", HttpMethod.GET,
                new HttpEntity<>(json(token("new@shop.test"))), Map.class);
        assertThat(me.getBody().get("email")).isEqualTo("new@shop.test");
    }

    @Test
    void login_wrong_password_is_401() {
        makeUser("wrong@shop.test", "customer");
        var body = "{\"email\":\"wrong@shop.test\",\"password\":\"nope\"}";
        var resp = rest.postForEntity("/auth/login", new HttpEntity<>(body, json(null)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void products_are_public_and_searchable() {
        var admin = makeUser("admin1@shop.test", "admin");
        createProduct(token("admin1@shop.test"), "Blue Mug", "5.00", 10);
        createProduct(token("admin1@shop.test"), "Red Plate", "6.00", 10);

        var resp = rest.getForEntity("/products?search=mug", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) resp.getBody().get("total")).intValue()).isEqualTo(1);
    }

    @Test
    void customer_cannot_create_product() {
        makeUser("cust@shop.test", "customer");
        var body = "{\"name\":\"X\",\"price\":1.00}";
        var resp = rest.postForEntity("/products",
                new HttpEntity<>(body, json(token("cust@shop.test"))), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void checkout_decrements_stock_and_clears_cart() {
        var admin = makeUser("admin2@shop.test", "admin");
        var adminTok = token("admin2@shop.test");
        Long productId = createProduct(adminTok, "Widget", "10.00", 5);

        makeUser("buyer@shop.test", "customer");
        var tok = token("buyer@shop.test");
        rest.postForEntity("/cart/items",
                new HttpEntity<>("{\"productId\":%d,\"quantity\":2}".formatted(productId), json(tok)), Map.class);

        var order = rest.postForEntity("/orders", new HttpEntity<>(json(tok)), Map.class);
        assertThat(order.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(order.getBody().get("createdAt")).isNotNull();       // @Generated works

        var product = rest.getForEntity("/products/" + productId, Map.class);
        assertThat(((Number) product.getBody().get("stock")).intValue()).isEqualTo(3);   // 5 - 2

        var cart = rest.exchange("/cart", HttpMethod.GET, new HttpEntity<>(json(tok)), Map.class);
        assertThat((java.util.List<?>) cart.getBody().get("items")).isEmpty();
    }

    @Test
    void checkout_insufficient_stock_is_409_and_no_change() {
        var adminTok = token(makeUser("admin3@shop.test", "admin").getEmail());
        Long productId = createProduct(adminTok, "Scarce", "10.00", 1);

        makeUser("buyer2@shop.test", "customer");
        var tok = token("buyer2@shop.test");
        rest.postForEntity("/cart/items",
                new HttpEntity<>("{\"productId\":%d,\"quantity\":5}".formatted(productId), json(tok)), Map.class);

        var resp = rest.postForEntity("/orders", new HttpEntity<>(json(tok)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var product = rest.getForEntity("/products/" + productId, Map.class);
        assertThat(((Number) product.getBody().get("stock")).intValue()).isEqualTo(1);   // untouched
    }

    private Long createProduct(String adminToken, String name, String price, int stock) {
        var body = "{\"name\":\"%s\",\"price\":%s,\"stock\":%d}".formatted(name, price, stock);
        var resp = rest.postForEntity("/products", new HttpEntity<>(body, json(adminToken)), Map.class);
        return ((Number) resp.getBody().get("id")).longValue();
    }
}
