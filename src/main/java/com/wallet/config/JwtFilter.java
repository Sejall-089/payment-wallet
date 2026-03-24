package com.wallet.config;

import com.wallet.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // no token present — pass through, SecurityConfig will decide if allowed
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // strip "Bearer " prefix to get the raw token
        String token = authHeader.substring(7);

        try {
            var userId = jwtUtil.extractUserId(token);
            var email  = jwtUtil.extractEmail(token);

            // tell Spring Security: this request is authenticated as this user
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId,       // principal — who they are (UUID)
                    null,         // credentials — null because JWT already proved identity
                    List.of()     // authorities — empty for now, add roles later
            );
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException e) {
            // invalid or expired token — don't set auth, let SecurityConfig reject it
            // don't return 401 here — let the filter chain handle it
        }

        filterChain.doFilter(request, response);
    }
}