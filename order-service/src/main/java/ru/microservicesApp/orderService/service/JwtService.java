package ru.microservicesApp.orderService.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import ru.microservicesApp.orderService.dto.auth.AuthResponse;

@Service
public class JwtService {
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(JwtEncoder jwtEncoder,
                      @Value("${security.jwt.issuer:order-service}") String issuer,
                      @Value("${security.jwt.access-token-ttl:15m}") Duration accessTokenTtl,
                      @Value("${security.jwt.refresh-token-ttl:7d}") Duration refreshTokenTtl) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }
    public AuthResponse generateTokenPair(Authentication authentication, UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet refreshClaims = JwtClaimsSet.builder().issuer(issuer)
                .subject(userId.toString()).issuedAt(now).expiresAt(now.plus(refreshTokenTtl))
                .id(UUID.randomUUID().toString()).claim("token_use", "refresh")
                .claim("username", authentication.getName()).build();
        return refreshAccessToken(authentication, userId, encode(refreshClaims));
    }

    public AuthResponse refreshAccessToken(Authentication authentication, UUID userId, String refreshToken) {
        Instant now = Instant.now();
        JwtClaimsSet accessClaims = JwtClaimsSet.builder().issuer(issuer)
                .subject(userId.toString()).claim("username", authentication.getName()).issuedAt(now).expiresAt(now.plus(accessTokenTtl))
                .id(UUID.randomUUID().toString()).claim("token_use", "access")
                .claim("roles", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .toList())
                .build();
        return new AuthResponse(encode(accessClaims), "Bearer", accessTokenTtl.toSeconds(), refreshToken);
    }

    private String encode(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
