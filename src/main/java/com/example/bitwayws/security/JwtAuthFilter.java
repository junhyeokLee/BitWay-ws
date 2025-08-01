package com.example.bitwayws.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String servletPath = request.getServletPath();

        Set<String> excludeUrls = Set.of(
            "/api/auth/login",
            "/api/auth/reissue",
            "/users/register"
        );

        if (excludeUrls.contains(servletPath)) {
            filterChain.doFilter(request, response);
            return;
        }
        // Header 에서 Token 추출
        String accessToken = jwtUtil.resolveAccessToken(request);

        // Token 유효성 검사
        if (accessToken != null && jwtUtil.validateToken(accessToken)) {
            // 토큰 유효 -> 토큰에서 Authentication 객체 SecurityContext 에 저장
            Authentication authentication = jwtUtil.getAuthentication(accessToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
