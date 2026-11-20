package com.lambdashield.fraude.presentation.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Spring Security procesa POST /login y POST /logout con protección CSRF. */
@Controller
public class LoginController {
    @GetMapping("/login")
    public String getLogin(HttpSession session, Model model, @RequestParam(required = false) String error) {
        var identidad = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (identidad != null && identidad.getPrincipal() instanceof com.lambdashield.fraude.application.service.AuthService.UsuarioSesion usuario) {
            return "CLIENTE".equals(usuario.rol()) ? "redirect:/operacion" : "redirect:/";
        }
        if (error != null) model.addAttribute("error", true);
        return "login";
    }
}
