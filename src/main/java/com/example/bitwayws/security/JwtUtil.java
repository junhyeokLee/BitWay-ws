package com.example.bitwayws.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final UserDetailsService userDetailsService;
    private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS512;
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String PREFIX = "Bearer ";
    private static final Long ACCESS_TOKEN_TIME = 1000L * 60 * 60 * 6;     // 6 시간
    private static final Long REFRESH_TOKEN_TIME = 1000L * 60 * 60 * 12;    // 12 시간

    @Value("${jwt.secret.key}")
    private String secretKey;

    private Key key;

    // 객체 초기화
    @PostConstruct
    protected void init() {
        byte[] bytes = Base64.getDecoder().decode(secretKey);
        key = Keys.hmacShaKeyFor(bytes);
    }

    // Header 에서 Token 가져오기
    public String resolveAccessToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(PREFIX)) {
            return bearerToken.substring(PREFIX.length());
        }
        return null;
    }

    // Access Token 생성
    public String createAccessToken(String email, List<String> authorities) {
        Claims claims = Jwts.claims().setSubject(email);
        claims.put("auth", authorities);

        Date now = new Date();

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ACCESS_TOKEN_TIME))
                .signWith(key, signatureAlgorithm)
                .compact();
    }

    // Refresh Token 생성
    public String createRefreshToken(String id, List<String> authorities, String userId) {
        Claims claims = Jwts.claims().setSubject(id);
        claims.put("auth", authorities);
        claims.put("userId", userId);

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + REFRESH_TOKEN_TIME);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, signatureAlgorithm)
                .compact();
    }

    public LocalDateTime getRefreshTokenExpiryDate() {
        return LocalDateTime.now().plusHours(12);
    }

    // 인증 객체 생성
    public Authentication getAuthentication(String token) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(this.getSubjectFromToken(token));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    // 토큰 검증
    public boolean validateToken(String token) {
        try {
            Jws<Claims> claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return !claims.getBody().getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    // 토큰에서 사용자 정보 가져오기
    public String getSubjectFromToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().getSubject();
    }
}