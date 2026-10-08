package ru.microservicesApp.orderService.security;

import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import ru.microservicesApp.orderService.config.SecurityConfig;
import ru.microservicesApp.orderService.controller.AuthController;
import ru.microservicesApp.orderService.controller.UserController;
import ru.microservicesApp.orderService.entity.Role;
import ru.microservicesApp.orderService.entity.User;
import ru.microservicesApp.orderService.exception.GlobalExceptionHandler;
import ru.microservicesApp.orderService.mapper.UserMapper;
import ru.microservicesApp.orderService.repository.UserRepository;
import ru.microservicesApp.orderService.service.AuthService;
import ru.microservicesApp.orderService.service.JwtService;
import ru.microservicesApp.orderService.service.UserService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AuthSecurityTests.TestConfig.class)
@TestPropertySource(properties = {
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.issuer=order-service",
        "security.jwt.access-token-ttl=15m"
})
class AuthSecurityTests {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, CustomUserDetailsService.class, AuthService.class, JwtService.class,
            AuthController.class, GlobalExceptionHandler.class,
            UserController.class, UserService.class, UserMapper.class})
    static class TestConfig {
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean static ConversionService conversionService() { return new ApplicationConversionService(); }
    }

    @Autowired WebApplicationContext context;
    @Autowired UserRepository repository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Autowired @Qualifier("refreshJwtDecoder") JwtDecoder refreshDecoder;
    private MockMvc mvc;
    private User user;

    @BeforeEach
    void setUp() {
        reset(repository);
        user = new User();
        user.setId(new UUID(0, 7));
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPassword(passwordEncoder.encode("secret-password"));
        user.setRoles(new HashSet<>(Set.of(Role.ROLE_ADMIN)));
        when(repository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(repository.findAll()).thenReturn(List.of(user));
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void issuesSignedTokenWithExpiryAndRoleWithoutCreatingSession() throws Exception {
        var result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", "secret-password")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.password").doesNotExist()).andReturn();
        assertNull(result.getRequest().getSession(false));
        Jwt token = decoder.decode(JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"));
        assertEquals(user.getId().toString(), token.getSubject());
        assertEquals("order-service", token.getClaimAsString("iss"));
        assertEquals(List.of("ROLE_ADMIN"), token.getClaimAsStringList("roles"));
        assertEquals(900, token.getExpiresAt().getEpochSecond() - token.getIssuedAt().getEpochSecond());
        Jwt refresh = refreshDecoder.decode(JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken"));
        assertEquals(604800, refresh.getExpiresAt().getEpochSecond() - refresh.getIssuedAt().getEpochSecond());
    }

    @Test
    void wrongPasswordAndUnknownUsernameReturnSameUnauthorizedResponse() throws Exception {
        String first = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", "wrong-password")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("unknown", "secret-password")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals(first, second);
        assertFalse(first.contains("accessToken"));
    }

    @Test
    void validatesLoginBeforeAccessingRepository() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void requiresBearerTokenAndAllowsAdminToReadUsers() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void regularUserCannotManageUsers() throws Exception {
        user.setRoles(new HashSet<>(Set.of(Role.ROLE_USER)));
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + login()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsChangedSignature() throws Exception {
        String token = login();
        int signatureStart = token.lastIndexOf('.') + 1;
        String tampered = token.substring(0, signatureStart)
                + (token.charAt(signatureStart) == 'A' ? 'B' : 'A')
                + token.substring(signatureStart + 1);
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredWrongIssuerAndMissingExpiryTokens() throws Exception {
        Instant now = Instant.now();
        List<JwtClaimsSet> invalidClaims = List.of(
                claims("order-service").issuedAt(now.minusSeconds(600)).expiresAt(now.minusSeconds(120)).build(),
                claims("other-service").issuedAt(now).expiresAt(now.plusSeconds(900)).build(),
                claims("order-service").issuedAt(now).build());
        for (JwtClaimsSet claims : invalidClaims) {
            String token = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }


    @Test
    void registersRegularUserAndHashesPasswordRegardlessOfSubmittedRole() throws Exception {
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        var result = mvc.perform(post("/auth/reg").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"email\":\"bob@example.com\","
                                + "\"password\":\"secret-password\",\"roles\":[\"ROLE_USER\",\"ROLE_ADMIN\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty()).andReturn();
        var captured = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(repository).save(captured.capture());
        assertEquals(Set.of(Role.ROLE_USER), captured.getValue().getRoles());
        assertTrue(passwordEncoder.matches("secret-password", captured.getValue().getPassword()));
        Jwt access = decoder.decode(JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"));
        assertEquals(List.of("ROLE_USER"), access.getClaimAsStringList("roles"));
    }

    @Test
    void rejectsDuplicateUsernameOrEmailAndInvalidRegistration() throws Exception {
        String body = "{\"username\":\"bob\",\"email\":\"bob@example.com\",\"password\":\"secret-password\"}";
        when(repository.save(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("private constraint details"));
        mvc.perform(post("/auth/reg").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
        clearInvocations(repository);
        mvc.perform(post("/auth/reg").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"email\":\"invalid\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void refreshesAccessTokenWithCurrentRoleWithoutExtendingRefreshLifetime() throws Exception {
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", "secret-password")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refresh = JsonPath.read(response, "$.refreshToken");
        Jwt oldAccess = decoder.decode(JsonPath.read(response, "$.accessToken"));
        user.setRoles(new HashSet<>(Set.of(Role.ROLE_USER)));
        var refreshed = mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refresh)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.refreshToken").value(refresh))
                .andReturn().getResponse().getContentAsString();
        Jwt newAccess = decoder.decode(JsonPath.read(refreshed, "$.accessToken"));
        assertNotEquals(oldAccess.getId(), newAccess.getId());
        assertEquals(List.of("ROLE_USER"), newAccess.getClaimAsStringList("roles"));
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAccessTokenMalformedAndExpiredRefreshTokens() throws Exception {
        String access = login();
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(access)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody("invalid")))
                .andExpect(status().isUnauthorized());
        Instant now = Instant.now();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("order-service").subject(user.getId().toString())
                        .claim("username", "alice").claim("token_use", "refresh")
                        .issuedAt(now.minusSeconds(600)).expiresAt(now.minusSeconds(120)).build())).getTokenValue();
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(expired)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshSurvivesRenameAndRejectsDeletedUser() throws Exception {
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", "secret-password")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refresh = JsonPath.read(response, "$.refreshToken");
        user.setUsername("renamed");
        String updated = mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refresh)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Jwt access = decoder.decode(JsonPath.read(updated, "$.accessToken"));
        assertEquals(user.getId().toString(), access.getSubject());
        assertEquals("renamed", access.getClaimAsString("username"));
        when(repository.findById(user.getId())).thenReturn(Optional.empty());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRefreshWithMissingOrBlankIdentityClaimsBeforeRepositoryAccess() throws Exception {
        Instant now = Instant.now();
        List<JwtClaimsSet> invalidClaims = List.of(
                JwtClaimsSet.builder().claim("username", "alice").build(),
                JwtClaimsSet.builder().subject("not-a-uuid").build(),
                JwtClaimsSet.builder().subject(" ").claim("username", "alice").build(),
                JwtClaimsSet.builder().subject("1-1-1-1-1").build());
        for (JwtClaimsSet identity : invalidClaims) {
            JwtClaimsSet claims = JwtClaimsSet.builder().claims(values -> values.putAll(identity.getClaims()))
                    .issuer("order-service").issuedAt(now).expiresAt(now.plusSeconds(900))
                    .claim("token_use", "refresh").build();
            String token = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(token)))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void loginAndRefreshIncludeBothRolesAndPermitAdminAccess() throws Exception {
        user.setRoles(new HashSet<>(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN)));
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", "secret-password")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = JsonPath.read(response, "$.accessToken");
        assertEquals(Set.of("ROLE_USER", "ROLE_ADMIN"),
                Set.copyOf(decoder.decode(access).getClaimAsStringList("roles")));
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roles.length()").value(2));
        String refreshed = mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(JsonPath.read(response, "$.refreshToken"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(Set.of("ROLE_USER", "ROLE_ADMIN"),
                Set.copyOf(decoder.decode(JsonPath.read(refreshed, "$.accessToken")).getClaimAsStringList("roles")));
    }

    @Test
    void adminCanAssignBothRolesButEmptyNullAndUnknownRolesAreRejected() throws Exception {
        String access = login();
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        String fields = "{\"username\":\"bob\",\"email\":\"bob@example.com\",\"password\":\"secret-password\",\"roles\":";
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fields + "[\"ROLE_USER\",\"ROLE_ADMIN\"]}"))
                .andExpect(status().isOk());
        var captured = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(repository).save(captured.capture());
        assertEquals(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN), captured.getValue().getRoles());
        clearInvocations(repository);
        for (String roles : List.of("[]", "[null]", "[\"ROLE_MANAGER\"]")) {
            mvc.perform(post("/api/users").header("Authorization", "Bearer " + access)
                            .contentType(MediaType.APPLICATION_JSON).content(fields + roles + "}"))
                    .andExpect(status().isBadRequest());
        }
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsPasswordsExceedingBcryptByteLimitBeforeRepositoryAccess() throws Exception {
        String password = "я".repeat(37);
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", password)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").exists());
        mvc.perform(post("/auth/reg").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"email\":\"bob@example.com\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void permitsExistingShortPasswordForLoginButNotRegistration() throws Exception {
        user.setPassword(passwordEncoder.encode("short"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody("alice", "short")))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/reg").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"email\":\"bob@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingEmptyNullUnknownAndNonListAccessRoles() throws Exception {
        List<Object> invalidRoles = List.of(List.of(), java.util.Arrays.asList((String) null),
                List.of("ROLE_MANAGER"), "ROLE_ADMIN", 123);
        for (Object roles : invalidRoles) {
            String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                    claims("order-service").expiresAt(Instant.now().plusSeconds(900)).claim("roles", roles).build()))
                    .getTokenValue();
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        JwtClaimsSet noRoles = JwtClaimsSet.builder().issuer("order-service").subject(user.getId().toString())
                .claim("token_use", "access").expiresAt(Instant.now().plusSeconds(900)).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), noRoles)).getTokenValue();
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validRefreshSubjectDoesNotRequireUsernameClaim() throws Exception {
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("order-service").subject(user.getId().toString())
                        .claim("token_use", "refresh").expiresAt(Instant.now().plusSeconds(900)).build())).getTokenValue();
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(token)))
                .andExpect(status().isOk());
        verify(repository).findById(user.getId());
        verify(repository, never()).findByUsername(anyString());
    }

    @Test
    void authenticationBackendFailureReturnsServerError() throws Exception {
        when(repository.findByUsername("alice")).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("private database details"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", "secret-password")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
    }

    @Test
    void rejectsRefreshWithWrongIssuerMissingExpiryOrChangedSignatureBeforeRepositoryAccess() throws Exception {
        Instant now = Instant.now();
        List<JwtClaimsSet> invalidClaims = List.of(
                JwtClaimsSet.builder().issuer("other-service").subject(user.getId().toString())
                        .claim("token_use", "refresh").expiresAt(now.plusSeconds(900)).build(),
                JwtClaimsSet.builder().issuer("order-service").subject(user.getId().toString())
                        .claim("token_use", "refresh").build());
        for (JwtClaimsSet claims : invalidClaims) {
            String token = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(token)))
                    .andExpect(status().isUnauthorized());
        }
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("order-service").subject(user.getId().toString())
                        .claim("token_use", "refresh").expiresAt(now.plusSeconds(900)).build())).getTokenValue();
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(tampered)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    private String refreshBody(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    private JwtClaimsSet.Builder claims(String issuer) {
        return JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString()).claim("token_use", "access").claim("roles", List.of("ROLE_ADMIN"));
    }

    private String login() throws Exception {
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", "secret-password")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}