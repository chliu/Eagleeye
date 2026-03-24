package com.eagleeye.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

@Controller
public class DashboardController {

    private static final Set<Integer> ALLOWED_DAYS = Set.of(20, 40, 60);

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "40") int days, Model model) {
        if (!ALLOWED_DAYS.contains(days)) days = 40;
        model.addAttribute("vm", service.buildViewModel(days));
        return "dashboard";
    }
}
