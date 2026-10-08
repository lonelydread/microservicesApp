package ru.microservicesApp.inventoryService.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.microservicesApp.inventoryService.dto.product.ProductRequest;
import ru.microservicesApp.inventoryService.entity.Product;
import ru.microservicesApp.inventoryService.mapper.ProductMapper;
import ru.microservicesApp.inventoryService.service.ProductService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductValidationTests {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsZeroValuesAndMaximumDatabaseBoundaries() {
        assertTrue(validator.validate(new ProductRequest("Product", null, BigDecimal.ZERO, 0)).isEmpty());
        assertTrue(validator.validate(new ProductRequest("a".repeat(255), "b".repeat(255),
                new BigDecimal("99999999999999999.99"), Integer.MAX_VALUE)).isEmpty());
        assertTrue(validator.validate(new ProductRequest("Product", "", new BigDecimal("1.23"), 1)).isEmpty());
    }

    @Test
    void reportsAllMissingRequiredFields() {
        assertEquals(Set.of("name", "price", "quantity"),
                invalidFields(new ProductRequest(null, null, null, null)));
    }

    @Test
    void rejectsBlankAndOversizedNames() {
        for (String name : List.of("", " \t\n", "a".repeat(256))) {
            assertEquals(Set.of("name"), invalidFields(new ProductRequest(name, null, BigDecimal.ONE, 1)));
        }
    }

    @Test
    void rejectsOversizedDescriptionAndNegativeQuantity() {
        assertEquals(Set.of("description", "quantity"),
                invalidFields(new ProductRequest("Product", "a".repeat(256), BigDecimal.ONE, -1)));
    }

    @Test
    void rejectsNegativeOverpreciseAndOversizedPrices() {
        for (String price : List.of("-0.01", "1.001", "1.2300", "100000000000000000.00", "1E+18", "0.0001")) {
            assertEquals(Set.of("price"), invalidFields(new ProductRequest("Product", null, new BigDecimal(price), 1)));
        }
    }

    @Test
    void returnsBadRequestAndDoesNotCallServiceForInvalidProduct() throws Exception {
        ProductService service = mock(ProductService.class);
        MockMvc mvc = mvc(service);
        var response = mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"price\":-1,\"quantity\":-1}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        String body = response.getContentAsString();
        assertTrue(body.contains("\"errors\""));
        assertTrue(body.contains("\"name\""));
        assertTrue(body.contains("\"price\""));
        assertTrue(body.contains("\"quantity\""));
        verifyNoInteractions(service);
    }

    @Test
    void acceptsValidProductThroughHttp() throws Exception {
        ProductService service = mock(ProductService.class);
        when(service.createProduct(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(7L);
            return product;
        });
        mvc(service).perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":25.50,\"quantity\":3}"))
                .andExpect(status().isCreated());
        verify(service).createProduct(any(Product.class));
    }

    @Test
    void rejectsNonPositiveAndNonNumericIdsBeforeCallingService() throws Exception {
        ProductService service = mock(ProductService.class);
        MockMvc mvc = mvc(service);
        for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
            var getResponse = mvc.perform(get("/api/products/" + id))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertTrue(getResponse.getContentAsString().contains("\"product_id\""));
            mvc.perform(delete("/api/products/" + id)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingMalformedAndIncorrectlyTypedBodies() throws Exception {
        ProductService service = mock(ProductService.class);
        MockMvc mvc = mvc(service);
        for (String body : List.of("", "null", "{", "{\"price\":\"abc\"}",
                "{\"quantity\":2147483648}", "{}")) {
            mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    private Set<String> invalidFields(ProductRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private MockMvc mvc(ProductService service) {
        return MockMvcBuilders.standaloneSetup(new ProductController(service, new ProductMapper()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
