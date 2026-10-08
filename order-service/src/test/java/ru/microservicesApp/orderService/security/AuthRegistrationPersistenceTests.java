package ru.microservicesApp.orderService.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.microservicesApp.orderService.config.SecurityConfig;
import ru.microservicesApp.orderService.dto.auth.AuthResponse;
import ru.microservicesApp.orderService.dto.auth.RegisterRequest;
import ru.microservicesApp.orderService.entity.Role;
import ru.microservicesApp.orderService.entity.User;
import ru.microservicesApp.orderService.mapper.UserMapper;
import ru.microservicesApp.orderService.repository.UserRepository;
import ru.microservicesApp.orderService.service.AuthService;
import ru.microservicesApp.orderService.service.JwtService;
import ru.microservicesApp.orderService.service.UserService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AuthService.class, JwtService.class, UserService.class, UserMapper.class,
        AuthRegistrationPersistenceTests.TestConfig.class})
@TestPropertySource(properties = {
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.issuer=order-service"
})
class AuthRegistrationPersistenceTests {
    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }
        @Bean AuthenticationManager authenticationManager() { return mock(AuthenticationManager.class); }
        @Bean("refreshJwtDecoder") JwtDecoder refreshJwtDecoder() { return mock(JwtDecoder.class); }
        @Bean JwtEncoder jwtEncoder(@Value("${security.jwt.secret-base64}") String secretBase64) {
            SecurityConfig config = new SecurityConfig();
            return spy(config.jwtEncoder(config.jwtSecretKey(secretBase64)));
        }
    }

    @Autowired AuthService authService;
    @Autowired UserRepository repository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtEncoder encoder;
    @Value("${security.jwt.secret-base64}") String secretBase64;
    @Value("${security.jwt.issuer:order-service}") String issuer;

    @BeforeEach
    void setUp() {
        reset(encoder);
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void registrationCommitsUserBeforeReturningTokens() {
        String password = "я".repeat(36); // Exactly 72 UTF-8 bytes, the BCrypt limit.
        AuthResponse response = authService.register(new RegisterRequest("alice", "alice@example.com", password));
        User saved = repository.findAll().getFirst();
        assertEquals(Set.of(Role.ROLE_USER), saved.getRoles());
        assertNotEquals(password, saved.getPassword());
        assertTrue(passwordEncoder.matches(password, saved.getPassword()));
        assertEquals(1, repository.count());
        SecurityConfig config = new SecurityConfig();
        var token = config.jwtDecoder(config.jwtSecretKey(secretBase64), issuer).decode(response.accessToken());
        assertEquals(saved.getId().toString(), token.getSubject());
        assertEquals(List.of("ROLE_USER"), token.getClaimAsStringList("roles"));
    }

    @Test
    void duplicateUsernameFailsAtCommitAndDoesNotReturnTokens() {
        authService.register(new RegisterRequest("alice", "alice@example.com", "secret-password"));
        clearInvocations(encoder);
        assertThrows(DataIntegrityViolationException.class,
                () -> authService.register(new RegisterRequest("alice", "other@example.com", "secret-password")));
        assertEquals(1, repository.count());
        // Encoding can happen in memory, but the transactional proxy does not return a result on failed commit.
        verify(encoder, times(2)).encode(any());
    }

    @Test
    void duplicateEmailRollsBackRegistration() {
        authService.register(new RegisterRequest("alice", "alice@example.com", "secret-password"));
        assertThrows(DataIntegrityViolationException.class,
                () -> authService.register(new RegisterRequest("bob", "alice@example.com", "secret-password")));
        assertEquals(1, repository.count());
    }

    @Test
    void encodingFailureRollsBackUserCreation() {
        doThrow(new JwtEncodingException("test encoding failure")).when(encoder).encode(any());
        assertThrows(JwtEncodingException.class,
                () -> authService.register(new RegisterRequest("alice", "alice@example.com", "secret-password")));
        assertEquals(0, repository.count());
    }

    @Test
    void jpaValidatesEntityBeforeInsertion() {
        List<Consumer<User>> invalidValues = List.of(
                user -> user.setUsername(" "),
                user -> user.setUsername("x".repeat(256)),
                user -> user.setEmail("invalid-email"),
                user -> user.setPassword(" "),
                user -> user.setRoles(new HashSet<>()),
                user -> user.setRoles(new HashSet<>(java.util.Collections.singleton(null))));
        for (Consumer<User> invalidValue : invalidValues) {
            User user = validUser();
            invalidValue.accept(user);
            assertThrows(ConstraintViolationException.class, () -> repository.saveAndFlush(user));
            assertEquals(0, repository.count());
        }
    }

    @Test
    void jpaValidatesEntityBeforeUpdate() {
        User saved = repository.saveAndFlush(validUser());
        saved.setUsername(" ");
        assertThrows(ConstraintViolationException.class, () -> repository.saveAndFlush(saved));
        assertEquals("alice", repository.findById(saved.getId()).orElseThrow().getUsername());
    }

    private User validUser() {
        User user = new User();
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPassword(passwordEncoder.encode("secret-password"));
        user.setRoles(new HashSet<>(Set.of(Role.ROLE_USER)));
        return user;
    }
}
