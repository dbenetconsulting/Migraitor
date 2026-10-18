package com.migration.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String root() {
        return "redirect:/migrationtool";
    }

    @GetMapping("/migrationtool")
    public String app(Model model, Principal user) {
        model.addAttribute("user", user == null ? "" : user.getName());
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
