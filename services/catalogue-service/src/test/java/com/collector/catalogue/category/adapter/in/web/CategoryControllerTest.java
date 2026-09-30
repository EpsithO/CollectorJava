package com.collector.catalogue.category.adapter.in.web;

import static org.hamcrest.Matchers.hasKey;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.collector.catalogue.category.application.ListCategories;
import com.collector.catalogue.category.domain.Category;
import com.collector.catalogue.shared.config.SecurityConfig;

/** Tranche web : contrat HTTP et sécurité, sans base. */
@WebMvcTest(CategoryController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class CategoryControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ListCategories listCategories;
    @MockitoBean JwtDecoder jwtDecoder;      // la tranche web ne crée pas le décodeur

    @Test
    void listIsPublicAndSnakeCase() throws Exception {
        when(listCategories.execute())
                .thenReturn(List.of(new Category(UUID.randomUUID(), "sneakers", "Baskets", null)));

        mvc.perform(get("/api/v1/categories"))         // sans jeton
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].slug").value("sneakers"))
           .andExpect(jsonPath("$[0]", hasKey("parent_id")));
    }

    @Test
    void apiResponsesCarryAStrictContentSecurityPolicy() throws Exception {
        when(listCategories.execute()).thenReturn(List.of());

        mvc.perform(get("/api/v1/categories"))
           .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                   .string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }
}
