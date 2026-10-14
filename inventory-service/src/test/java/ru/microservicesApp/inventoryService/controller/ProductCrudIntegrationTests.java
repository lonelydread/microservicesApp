package ru.microservicesApp.inventoryService.controller;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.microservicesApp.inventoryService.repository.ProductRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:product-crud;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.grpc.server.enabled=false"
})
class ProductCrudIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ProductRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createsListsReadsAndDeletesAPersistedProduct() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(product("Keyboard", "25.50", "12.50", 5)))
                .andExpect(status().isOk());
        var saved = repository.findAll().getFirst();
        assertNotNull(saved.getId());
        assertEquals(new BigDecimal("25.50"), saved.getPrice());
        assertEquals(new BigDecimal("12.50"), saved.getSale());

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(saved.getId().toString()));
        mvc.perform(get("/api/products/{id}", saved.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.sale").value(12.5));
        mvc.perform(delete("/api/products/{id}", saved.getId())).andExpect(status().isOk());
        assertFalse(repository.existsById(saved.getId()));
        mvc.perform(get("/api/products/{id}", saved.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidBodiesDoNotCreateRows() throws Exception {
        for (String body : new String[]{"{}", "{\"name\":\"Keyboard\"}",
                product("", "25.50", "0", 1), product("Keyboard", "-1", "0", 1),
                product("Keyboard", "25.50", "101", 1), product("Keyboard", "25.50", "0", -1), product("Keyboard", "25.50", "0", 0),
                product("Keyboard", "25.50", "0", 1).replace("\"quantity\":1", "\"quantity\":null")}) {
            mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, repository.count());
    }

    private String product(String name, String price, String sale, int quantity) {
        return """
                {"name":"%s","description":"Test product","price":%s,"sale":%s,"quantity":%d}
                """.formatted(name, price, sale, quantity);
    }
}
