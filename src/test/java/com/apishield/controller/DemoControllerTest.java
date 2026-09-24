package com.apishield.controller;

import com.apishield.service.ClientService;
import com.apishield.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DemoController.class)
@AutoConfigureMockMvc(addFilters = false)
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientService clientService;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void getProducts_returnsThreeItems() throws Exception {
        mockMvc.perform(get("/api/demo/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name", is("Widget Pro")))
                .andExpect(jsonPath("$[1].name", is("Gadget Plus")))
                .andExpect(jsonPath("$[2].name", is("Doohickey Max")));
    }
}
