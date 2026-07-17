package com.eagleeye.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock DashboardService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/WEB-INF/templates/");
        viewResolver.setSuffix(".html");
        mvc = MockMvcBuilders.standaloneSetup(new DashboardController(service))
            .setViewResolvers(viewResolver)
            .build();
    }

    DashboardViewModel emptyVm(int days) {
        return new DashboardViewModel(
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), days);
    }

    @Test
    void dashboard_defaultDays_is40() throws Exception {
        when(service.buildViewModel(40)).thenReturn(emptyVm(40));

        mvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard"))
            .andExpect(model().attributeExists("vm"));
    }

    @Test
    void dashboard_acceptsValidDays() throws Exception {
        when(service.buildViewModel(20)).thenReturn(emptyVm(20));

        mvc.perform(get("/dashboard?days=20"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("vm", emptyVm(20)));
    }

    @Test
    void dashboard_normalizesInvalidDaysTo40() throws Exception {
        when(service.buildViewModel(40)).thenReturn(emptyVm(40));

        mvc.perform(get("/dashboard?days=999"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("vm", emptyVm(40)));
    }
}
