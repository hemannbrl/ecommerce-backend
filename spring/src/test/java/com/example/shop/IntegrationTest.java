package com.example.shop;

import com.example.shop.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base for integration tests. A single PostgreSQL container (started once and
 * shared across all test classes) is loaded with the shared ../db/schema.sql,
 * so tests run against the exact production schema.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class IntegrationTest {

    // Singleton container: started once for the whole JVM, reused by every test
    // class (Testcontainers' Ryuk removes it at exit). This keeps the cached
    // Spring context valid across classes.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../db/schema.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    User makeUser(String email, String role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("Password123!"));
        u.setRole(role);
        return users.save(u);
    }
}
