package ru.microservicesApp.orderService.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
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

    public JwtService(JwtEncoder jwtEncoder,
                      @Value("${security.jwt.issuer:order-service}") String issuer,
                      @DurationMin(minutes = 15)
                      @DurationMax(hours = 1)
                      @Value("${security.jwt.access-token-ttl:15m}") Duration accessTokenTtl) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
    }

    public AuthResponse generateToken(Authentication authentication) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer)
                .subject(authentication.getName()).issuedAt(now).expiresAt(now.plus(accessTokenTtl))
                .id(UUID.randomUUID().toString())
                .claim("roles", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).filter(authority -> authority.startsWith("ROLE_")).toList())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", accessTokenTtl.toSeconds());
    }
}
