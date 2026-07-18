package com.example.shop;

import com.example.shop.domain.Cart;
import com.example.shop.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class AuthService {

    private final UserRepository users;
    private final CartRepository carts;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    AuthService(UserRepository users, CartRepository carts,
                PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.carts = carts;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    Dto.AuthResponse register(Dto.RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(req.email());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole("customer");
        users.save(user);

        Cart cart = new Cart();
        cart.setUserId(user.getId());
        carts.save(cart);

        return new Dto.AuthResponse(jwt.generate(user), toDto(user));
    }

    Dto.AuthResponse login(Dto.LoginRequest req) {
        User user = users.findByEmail(req.email())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        return new Dto.AuthResponse(jwt.generate(user), toDto(user));
    }

    static Dto.UserDto toDto(User user) {
        return new Dto.UserDto(user.getId(), user.getEmail(), user.getRole());
    }
}
