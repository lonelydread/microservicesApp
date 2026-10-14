package ru.microservicesApp.orderService.security;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.DispatcherType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import ru.microservicesApp.orderService.config.SecurityConfig;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.startsWith;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AuthSecurityTests.TestConfig.class)
@TestPropertySource(properties = {
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class AuthSecurityTests {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, TestEndpoint.class})
    static class TestConfig {
        @Bean UserDetailsService userDetailsService() { return mock(UserDetailsService.class); }
    }

    @RestController
    static class TestEndpoint {
        @GetMapping(value = "/api/users", produces = MediaType.TEXT_PLAIN_VALUE)
        public String users() { return "protected"; }

        @GetMapping(value = "/error", produces = MediaType.TEXT_PLAIN_VALUE)
        public String error() { return "error handled"; }
    }

    @Autowired WebApplicationContext context;
    @Autowired JwtEncoder encoder;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void rejectsChangedSignature() throws Exception {
        String token = encode(claims("order-service").expiresAt(Instant.now().plusSeconds(900)).build());
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""))
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
    }

    @Test
    void rejectsExpiredWrongIssuerAndMissingExpiryTokens() throws Exception {
        for (JwtClaimsSet invalid : List.of(
                claims("order-service").expiresAt(Instant.now().minusSeconds(120)).build(),
                claims("other-service").expiresAt(Instant.now().plusSeconds(900)).build(),
                claims("order-service").build())) {
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + encode(invalid)))
                    .andExpect(status().isUnauthorized()).andExpect(content().string(""));
        }
    }

    @Test
    void rejectsMissingEmptyUnknownAndNonListAccessRoles() throws Exception {
        for (Object roles : Arrays.asList(null, List.of(), List.of("ROLE_UNKNOWN"), "ROLE_ADMIN")) {
            var builder = claims("order-service").expiresAt(Instant.now().plusSeconds(900));
            builder.claims(values -> {
                values.remove("roles");
                if (roles != null) values.put("roles", roles);
            });
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + encode(builder.build())))
                    .andExpect(status().isUnauthorized()).andExpect(content().string(""));
        }
    }

    @Test
    void permitsInternalErrorDispatchButProtectsDirectRequests() throws Exception {
        mvc.perform(get("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; }))
                .andExpect(status().isOk()).andExpect(content().string("error handled"));
        mvc.perform(get("/error"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    private JwtClaimsSet.Builder claims(String issuer) {
        return JwtClaimsSet.builder().issuer(issuer).subject(UUID.randomUUID().toString())
                .claim("token_use", "access").claim("roles", List.of("ROLE_ADMIN"));
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
