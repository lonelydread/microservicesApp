package ru.microservicesApp.orderService.config;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean SecretKey key(@Value("${security.jwt.secret-base64}") String secretBase64) {
            return new SecurityConfig().jwtSecretKey(secretBase64);
        }
        @Bean JwtDecoder decoder(SecretKey key, @Value("${security.jwt.issuer:order-service}") String issuer) {
            return new SecurityConfig().jwtDecoder(key, issuer);
        }
    }

    @Test
    void startsWithValidSecretAndDefaultIssuer() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(JwtDecoder.class));
        });
    }

    @Test
    void rejectsBlankIssuerAtStartup() {
        runner.withPropertyValues("security.jwt.issuer= ")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void rejectsBlankMalformedAndShortSecretsAtStartup() {
        for (String secret : new String[]{"", " ", "invalid-base64!", "c2hvcnQ="}) {
            runner.withPropertyValues("security.jwt.secret-base64=" + secret)
                    .run(context -> assertNotNull(context.getStartupFailure()));
        }
    }

    @Test
    void rejectsMissingSecretAtStartup() {
        new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
