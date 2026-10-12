package ru.microservicesApp.inventoryService.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.microservicesApp.inventoryService.dto.product.ProductRequest;
import ru.microservicesApp.inventoryService.entity.Product;
import ru.microservicesApp.inventoryService.exception.GlobalExceptionHandler;
import ru.microservicesApp.inventoryService.exception.ProductNotFoundException;
import ru.microservicesApp.inventoryService.mapper.ProductMapper;
import ru.microservicesApp.inventoryService.service.ProductService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductValidationTests {
    private static final UUID PRODUCT_ID = UUID.randomUUID();
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
    void acceptsPositiveValuesAndMaximumDatabaseBoundaries() {
        assertTrue(validator.validate(new ProductRequest("a".repeat(255), "b".repeat(255),
                new BigDecimal("99999999999999999.99"), new BigDecimal("100.00"), Integer.MAX_VALUE)).isEmpty());
        assertTrue(validator.validate(new ProductRequest("Product", "", new BigDecimal("1.23"),
                BigDecimal.ZERO, 1)).isEmpty());
    }

    @Test
    void reportsAllMissingRequiredFields() {
        assertEquals(Set.of("name", "price", "sale", "quantity"),
                invalidFields(new ProductRequest(null, null, null, null, null)));
    }

    @Test
    void rejectsBlankAndOversizedNames() {
        for (String name : List.of("", " \t\n", "a".repeat(256))) {
            assertEquals(Set.of("name"), invalidFields(new ProductRequest(name, null, BigDecimal.ONE,
                    BigDecimal.ZERO, 1)));
        }
    }

    @Test
    void rejectsOversizedDescriptionAndNegativeQuantity() {
        assertEquals(Set.of("description", "quantity"),
                invalidFields(new ProductRequest("Product", "a".repeat(256), BigDecimal.ONE, BigDecimal.ZERO, -1)));
    }

    @Test
    void rejectsNonPositiveOverpreciseAndOversizedPrices() {
        for (String price : List.of("0", "-0.01", "1.001", "1.2300", "100000000000000000.00", "1E+18", "0.0001")) {
            assertEquals(Set.of("price"), invalidFields(new ProductRequest("Product", null, new BigDecimal(price),
                    BigDecimal.ZERO, 1)));
        }
    }

    @Test
    void acceptsSaleBoundariesAndFractionalPercentages() {
        for (String sale : List.of("0", "0.01", "12.50", "99.99", "100", "100.00")) {
            BigDecimal percentage = new BigDecimal(sale);
            assertTrue(validator.validate(new ProductRequest("Product", null, BigDecimal.ONE, percentage, 1)).isEmpty());
            Product product = new Product();
            product.setSale(percentage);
            assertTrue(validator.validate(product).isEmpty());
        }
        assertEquals(BigDecimal.ZERO, new Product().getSale());
    }

    @Test
    void rejectsMissingOutOfRangeAndOverpreciseSaleOnRequestAndEntity() {
        for (BigDecimal sale : new BigDecimal[] {null, new BigDecimal("-0.01"), new BigDecimal("100.01"),
                new BigDecimal("1000"), new BigDecimal("1.001")}) {
            assertEquals(Set.of("sale"), invalidFields(new ProductRequest("Product", null, BigDecimal.ONE, sale, 1)));
            Product product = new Product();
            product.setSale(sale);
            assertEquals(Set.of("sale"), validator.validate(product).stream()
                    .map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet()));
        }
    }

    @Test
    void returnsBadRequestAndDoesNotCallServiceForInvalidProduct() throws Exception {
        ProductService service = mock(ProductService.class);
        mvc(service).perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"price\":-1,\"sale\":0,\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.quantity").exists());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidSaleThroughHttpBeforeCallingService() throws Exception {
        ProductService service = mock(ProductService.class);
        MockMvc mvc = mvc(service);
        for (String sale : List.of("null", "-0.01", "100.01", "1.001", "\"abc\"")) {
            mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Keyboard\",\"price\":25.50,\"quantity\":3,\"sale\":" + sale + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":25.50,\"quantity\":3}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void acceptsValidProductWithSaleThroughHttp() throws Exception {
        ProductService service = mock(ProductService.class);
        when(service.createProduct(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            assertEquals(new BigDecimal("12.50"), product.getSale());
            product.setId(PRODUCT_ID);
            return product;
        });
        mvc(service).perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":25.50,\"sale\":12.50,\"quantity\":3}"))
                .andExpect(status().isOk());
        verify(service).createProduct(any(Product.class));
    }

    @Test
    void includesSaleInSingleProductAndProductListResponses() throws Exception {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setSale(new BigDecimal("12.50"));
        ProductService service = mock(ProductService.class);
        when(service.getProductById(7L)).thenReturn(product);
        when(service.getAllProducts()).thenReturn(List.of(product));
        MockMvc mvc = mvc(service);
        mvc.perform(get("/api/products/7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sale").value(12.50));
        mvc.perform(get("/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sale").value(12.50));
    }

    @Test
    void rejectsNonPositiveAndNonNumericIdsBeforeCallingService() throws Exception {
        ProductService service = mock(ProductService.class);
        MockMvc mvc = mvc(service);
        for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
            mvc.perform(get("/api/products/" + id)).andExpect(status().isBadRequest());
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

    @Test
    void returnsNotFoundForMissingProductsOnGetAndDelete() throws Exception {
        ProductService service = mock(ProductService.class);
        when(service.getProductById(7L)).thenThrow(new ProductNotFoundException(7L));
        doThrow(new ProductNotFoundException(7L)).when(service).deleteProduct(7L);
        MockMvc mvc = mvc(service);
        mvc.perform(get("/api/products/7")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product with id 7 not found"));
        mvc.perform(delete("/api/products/7")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product with id 7 not found"));
    }

    @Test
    void returnsConflictWithoutExposingDatabaseDetails() throws Exception {
        ProductService service = mock(ProductService.class);
        when(service.createProduct(any(Product.class)))
                .thenThrow(new DataIntegrityViolationException("Private database constraint details"));
        mvc(service).perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":25.50,\"sale\":0,\"quantity\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Data conflicts with database constraints"));
    }

    @Test
    void returnsInternalServerErrorWithoutExposingImplementationDetails() throws Exception {
        ProductService service = mock(ProductService.class);
        when(service.getAllProducts()).thenThrow(new IllegalStateException("Private database details"));
        mvc(service).perform(get("/api/products")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
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
