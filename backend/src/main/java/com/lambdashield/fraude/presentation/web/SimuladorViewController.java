package com.lambdashield.fraude.presentation.web;

import com.lambdashield.fraude.application.service.ClienteService;
import com.lambdashield.fraude.application.service.ReglaConsultaService;
import com.lambdashield.fraude.presentation.mapper.ReglaResponseMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SimuladorViewController {

    private final ClienteService clienteService;
    private final ReglaConsultaService reglas;
    private final ReglaResponseMapper mapper;

    public SimuladorViewController(ClienteService clienteService, ReglaConsultaService reglas, ReglaResponseMapper mapper) {
        this.clienteService = clienteService;
        this.reglas = reglas;
        this.mapper = mapper;
    }

    @GetMapping("/simulador")
    public String simulador(HttpSession session, Model model) {
        model.addAttribute("clientes", clienteService.listarTodos());
        var catalogo = reglas.listarTodas().stream().map(mapper::toResponse).toList();
        model.addAttribute("reglasActivas", catalogo.stream().filter(r -> r.activa()).toList());
        // Reglas inactivas con peso válido: se pueden probar como candidatas sin activarlas.
        model.addAttribute("reglasCandidatas", catalogo.stream().filter(r -> !r.activa() && r.pesoMaximo() != null).toList());
        model.addAttribute("nombreUsuario", session.getAttribute("nombreUsuario"));
        return "simulador";
    }
}
