package ru.microservicesApp.orderService.security;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import ru.microservicesApp.orderService.dto.auth.RefreshRequest;
import ru.microservicesApp.orderService.entity.Role;
import ru.microservicesApp.orderService.entity.User;
import ru.microservicesApp.orderService.mapper.UserMapper;
import ru.microservicesApp.orderService.repository.UserRepository;
import ru.microservicesApp.orderService.service.AuthService;
import ru.microservicesApp.orderService.service.JwtService;
import ru.microservicesApp.orderService.service.UserService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthRefreshFailureTests {
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final UserRepository repository = mock(UserRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthService service = new AuthService(mock(AuthenticationManager.class), jwtService, decoder,
            repository, mock(UserService.class), new UserMapper());
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Jwt token = Jwt.withTokenValue("refresh-token").header("alg", "HS256")
                .subject(userId.toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).build();
        when(decoder.decode("refresh-token")).thenReturn(token);
        User user = new User();
        user.setId(userId);
        user.setUsername("alice");
        user.setRoles(Set.of(Role.ROLE_USER));
        when(repository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void convertsOnlyDecodingFailureToBadCredentials() {
        BadJwtException failure = new BadJwtException("invalid refresh token");
        when(decoder.decode("refresh-token")).thenThrow(failure);
        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> service.refresh(new RefreshRequest("refresh-token")));
        assertSame(failure, exception.getCause());
        verifyNoInteractions(repository, jwtService);
    }

    @Test
    void databaseFailureIsNotClassifiedAsInvalidToken() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("test database failure");
        when(repository.findById(userId)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.refresh(new RefreshRequest("refresh-token"))));
        verifyNoInteractions(jwtService);
    }

    @Test
    void encodingFailureIsNotClassifiedAsInvalidToken() {
        JwtEncodingException failure = new JwtEncodingException("test encoding failure");
        when(jwtService.refreshAccessToken(any(), eq(userId), eq("refresh-token"))).thenThrow(failure);
        assertSame(failure, assertThrows(JwtEncodingException.class,
                () -> service.refresh(new RefreshRequest("refresh-token"))));
    }
}
