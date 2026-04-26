package com.example.demo.service;

import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.entity.User;
import com.example.demo.enums.UserRole;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtTokenProvider;
import com.example.demo.security.TokenBlocklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlocklistService tokenBlocklistService;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        String token = jwtTokenProvider.generateToken(auth);
        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .role(user.getRole())
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .build();
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already taken", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.USER)
                .build();

        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getUsername());
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .role(user.getRole())
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .build();
    }

    /**
     * Logout: revoke the current token by adding it to the blocklist.
     *
     * WHY WE NEED THE EXPIRY TIME:
     *   The blocklist entry should be kept only until the token naturally expires —
     *   after that, JwtTokenProvider.validateToken() rejects it on its own, so
     *   the blocklist entry is just wasted memory.
     *   We ask JwtTokenProvider for the token's expiry so the cleanup scheduler
     *   can remove the entry at the right time (see TokenBlocklistService.purgeExpired).
     *
     * The token string itself is used as the map key.  In production with Redis,
     * a shorter key (the "jti" — JWT ID — claim) is preferred to save memory.
     */
    public void logout(String token) {
        long expiryMs = jwtTokenProvider.getExpirationTimeFromToken(token);
        tokenBlocklistService.revoke(token, expiryMs);
    }
}
