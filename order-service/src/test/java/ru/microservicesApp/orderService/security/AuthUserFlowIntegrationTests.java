package ru.microservicesApp.orderService.security;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import ru.microservicesApp.orderService.dto.auth.LoginRequest;
import ru.microservicesApp.orderService.dto.user.UserRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import ru.microservicesApp.orderService.entity.Role;
import ru.microservicesApp.orderService.service.AuthService;
import ru.microservicesApp.orderService.dto.auth.RegisterRequest;
import ru.microservicesApp.orderService.entity.User;
import ru.microservicesApp.orderService.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-user-flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.kafka.admin.auto-create=false",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class AuthUserFlowIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository repository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtDecoder decoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @MockitoSpyBean AuthService authService;
    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void registrationPersistsAHashedPasswordAndLoginAuthenticatesAgainstTheDatabase() throws Exception {
        Tokens registered = register("alice", "alice@example.com", "secret-password");
        User saved = repository.findByUsername("alice").orElseThrow();
        assertNotEquals("secret-password", saved.getPassword());
        assertTrue(passwordEncoder.matches("secret-password", saved.getPassword()));
        assertEquals(saved.getId().toString(), decoder.decode(registered.accessToken()).getSubject());
        assertEquals(List.of("ROLE_USER"), decoder.decode(registered.accessToken()).getClaimAsStringList("roles"));
        assertEquals(1, roleCount(saved.getId()));
        Tokens loggedIn = login("alice", "secret-password");
        assertEquals(saved.getId().toString(), decoder.decode(loggedIn.accessToken()).getSubject());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "alice", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""));
    }

    @Test
    void duplicateUsernameOrEmailRollsBackRegistration() throws Exception {
        register("alice", "alice@example.com", "secret-password");
        for (var payload : List.of(
                Map.of("username", "alice", "email", "different@example.com", "password", "secret-password"),
                Map.of("username", "different", "email", "alice@example.com", "password", "secret-password"))) {
            mvc.perform(post("/auth/reg").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(payload)))
                    .andExpect(status().isConflict()).andExpect(content().string(""));
        }
        assertEquals(1, repository.count());
        assertEquals(1, jdbc.queryForObject("select count(*) from user_roles", Integer.class));
    }

    @Test
    void invalidRegistrationAndOversizedUtf8PasswordsNeverReachPersistence() throws Exception {
        for (var payload : List.of(
                Map.of("username", "", "email", "alice@example.com", "password", "secret-password"),
                Map.of("username", "alice", "email", "not-an-email", "password", "secret-password"),
                Map.of("username", "alice", "email", "alice@example.com", "password", "пароль".repeat(7)))) {
            mvc.perform(post("/auth/reg").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(payload))).andExpect(status().isBadRequest()).andExpect(content().string(""));
        }
        assertEquals(0, repository.count());
        String boundaryPassword = "я".repeat(36);
        register("boundary", "boundary@example.com", boundaryPassword);
        login("boundary", boundaryPassword);
        mvc.perform(post("/auth/reg").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "too-long", "email", "long@example.com",
                                "password", boundaryPassword + "a"))))
                .andExpect(status().isBadRequest()).andExpect(content().string(""));
        assertEquals(1, repository.count());
    }

    @Test
    void onlyAdminsCanManageUsersAndCrudPersistsBothRolesAndNewPassword() throws Exception {
        Tokens ordinary = register("customer", "customer@example.com", "secret-password");
        String admin = adminToken();
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        mvc.perform(get("/api/users").header("Authorization", bearer(ordinary.accessToken())))
                .andExpect(status().isForbidden()).andExpect(content().string(""));

        mvc.perform(post("/api/users").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(userBody("managed", "managed@example.com",
                                "initial-password", List.of("ROLE_USER", "ROLE_ADMIN"))))
                .andExpect(status().isOk());
        User saved = repository.findByUsername("managed").orElseThrow();
        assertEquals(2, roleCount(saved.getId()));
        for (List<String> roles : List.of(List.<String>of(), List.of("ROLE_UNKNOWN"))) {
            mvc.perform(put("/api/users/{id}", saved.getId()).header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(userBody("invalid-update", "new@example.com", "new-password", roles)))
                    .andExpect(status().isBadRequest()).andExpect(content().string(""));
        }
        assertEquals(2, roleCount(saved.getId()));
        mvc.perform(get("/api/users/{id}", saved.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.password").doesNotExist());

        mvc.perform(put("/api/users/{id}", saved.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(userBody("renamed", "renamed@example.com",
                                "replacement-password", List.of("ROLE_USER"))))
                .andExpect(status().isOk());
        assertEquals(1, roleCount(saved.getId()));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "renamed", "password", "initial-password"))))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""));
        login("renamed", "replacement-password");

        mvc.perform(delete("/api/users/{id}", saved.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        assertFalse(repository.existsById(saved.getId()));
        assertEquals(0, roleCount(saved.getId()));
        mvc.perform(get("/api/users/{id}", saved.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
    }

    @Test
    void refreshReadsCurrentUsernameAndRolesFromTheDatabase() throws Exception {
        Tokens original = register("alice", "alice@example.com", "secret-password");
        UUID userId = UUID.fromString(decoder.decode(original.accessToken()).getSubject());
        String admin = adminToken();
        mvc.perform(put("/api/users/{id}", userId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(userBody("renamed", "renamed@example.com",
                                "replacement-password", List.of("ROLE_USER", "ROLE_ADMIN"))))
                .andExpect(status().isOk());
        var result = mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", original.refreshToken()))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.refreshToken").value(original.refreshToken())).andReturn();
        Tokens refreshed = json.readValue(result.getResponse().getContentAsString(), Tokens.class);
        var jwt = decoder.decode(refreshed.accessToken());
        assertEquals(userId.toString(), jwt.getSubject());
        assertEquals("renamed", jwt.getClaimAsString("username"));
        assertEquals(Set.of("ROLE_USER", "ROLE_ADMIN"), Set.copyOf(jwt.getClaimAsStringList("roles")));
        mvc.perform(get("/api/users").header("Authorization", bearer(refreshed.accessToken())))
                .andExpect(status().isOk());
    }

    @Test
    void refreshRejectsDeletedUsersAndAccessTokensCannotReplaceRefreshTokens() throws Exception {
        Tokens tokens = register("alice", "alice@example.com", "secret-password");
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", tokens.accessToken()))))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mvc.perform(get("/api/users").header("Authorization", bearer(tokens.refreshToken())))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""));
        repository.deleteById(UUID.fromString(decoder.decode(tokens.accessToken()).getSubject()));
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""));
    }

    @Test
    void malformedAuthRequestsReturnBadRequestOverHttp() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        for (String path : List.of("/auth/login", "/auth/reg", "/auth/refresh")) {
            for (String body : List.of("{invalid-json", "")) {
                var response = httpPost(client, path, body);
                assertEquals(400, response.statusCode());
                assertEquals("", response.body());
            }
        }
        assertEquals(0, repository.count());
    }

    @Test
    void serverFailuresReturnCorrectStatusesWithEmptyBodies() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String body = json.writeValueAsString(Map.of("username", "alice", "email", "alice@example.com",
                "password", "secret-password"));
        for (RuntimeException failure : List.of(
                new DataAccessResourceFailureException("Private database details"),
                new CannotCreateTransactionException("Private connection details"))) {
            doThrow(failure).when(authService).register(any(RegisterRequest.class));
            var response = httpPost(client, "/auth/reg", body);
            assertEquals(503, response.statusCode());
            assertEquals("", response.body());
        }
        doThrow(new IllegalStateException("Private implementation details"))
                .when(authService).register(any(RegisterRequest.class));
        var response = httpPost(client, "/auth/reg", body);
        assertEquals(500, response.statusCode());
        assertEquals("", response.body());
        assertEquals(0, repository.count());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void authenticationRequestsDoNotLeakSecretsIntoLogs(CapturedOutput output) throws Exception {
        String password = "log-secret-password-75fbfba6";
        String refreshToken = "log-secret-refresh-8f98ff8e";
        for (Object request : List.of(
                new LoginRequest("log-user", password),
                new RegisterRequest("log-user", "log-user@example.com", password),
                new UserRequest("log-user", "log-user@example.com", password, Set.of(Role.ROLE_USER)))) {
            assertFalse(request.toString().contains(password));
            assertFalse(request.toString().contains("password"));
        }

        HttpClient client = HttpClient.newHttpClient();
        assertEquals(201, httpPost(client, "/auth/reg", json.writeValueAsString(Map.of(
                "username", "log-user", "email", "log-user@example.com", "password", password))).statusCode());
        assertEquals(200, httpPost(client, "/auth/login", json.writeValueAsString(Map.of(
                "username", "log-user", "password", password))).statusCode());

        String invalidPassword = "s3cret";
        assertEquals(400, httpPost(client, "/auth/reg", json.writeValueAsString(Map.of(
                "username", "invalid", "email", "invalid@example.com", "password", invalidPassword))).statusCode());
        assertEquals(401, httpPost(client, "/auth/refresh", json.writeValueAsString(Map.of(
                "refreshToken", refreshToken))).statusCode());

        assertFalse(output.getAll().contains(password));
        assertFalse(output.getAll().contains(invalidPassword));
        assertFalse(output.getAll().contains(refreshToken));
        assertFalse(output.getAll().contains("RegisterRequest["));
        assertFalse(output.getAll().contains("LoginRequest["));
        assertFalse(output.getAll().contains("RefreshRequest["));
    }

    private HttpResponse<String> httpPost(HttpClient client, String path, String body) throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Tokens register(String username, String email, String password) throws Exception {
        var result = mvc.perform(post("/auth/reg").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "email", email, "password", password))))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        return json.readValue(result.getResponse().getContentAsString(), Tokens.class);
    }

    private Tokens login(String username, String password) throws Exception {
        var result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return json.readValue(result.getResponse().getContentAsString(), Tokens.class);
    }

    private String adminToken() throws Exception {
        User admin = new User();
        admin.setUsername("administrator");
        admin.setEmail("admin@example.com");
        admin.setPassword(passwordEncoder.encode("admin-password"));
        admin.setRoles(new HashSet<>(Set.of(Role.ROLE_ADMIN)));
        repository.saveAndFlush(admin);
        return login("administrator", "admin-password").accessToken();
    }

    private int roleCount(UUID id) {
        return jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class, id);
    }

    private String userBody(String username, String email, String password, List<String> roles) {
        return json.writeValueAsString(Map.of("username", username, "email", email, "password", password, "roles", roles));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    record Tokens(String accessToken, String tokenType, long expiresIn, String refreshToken) {}
}
