package com.eagleeye.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class VpocPageController {

    @GetMapping("/vpoc")
    public String vpoc() {
        return "vpoc";
    }
}
