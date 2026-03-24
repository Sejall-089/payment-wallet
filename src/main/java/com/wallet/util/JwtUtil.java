package com.wallet.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;

    // reads values from application.yml — never hardcode secrets in Java
    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    // called after successful login — creates and signs a token
    public String generateToken(UUID userId, String email) {
        return Jwts.builder()
                .subject(userId.toString())   // who this token is for
                .claim("email", email)        // extra data inside the token
                .issuedAt(new Date())         // when it was created
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)          // sign with your secret — tamper-proof
                .compact();                   // build the final string
    }

    // called on every request — verify the token is genuine and not expired
    public Claims validateAndExtract(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)        // use same key to verify signature
                .build()
                .parseSignedClaims(token)
                .getPayload();               // returns the claims if valid, throws if not
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(validateAndExtract(token).getSubject());
    }

    public String extractEmail(String token) {
        return validateAndExtract(token).get("email", String.class);
    }
}