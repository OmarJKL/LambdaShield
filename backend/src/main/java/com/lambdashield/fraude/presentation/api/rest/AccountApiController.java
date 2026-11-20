package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.service.CuentaDestinoService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account/destination")
public class AccountApiController {
    private final CuentaDestinoService cuentas;
    public AccountApiController(CuentaDestinoService cuentas) { this.cuentas = cuentas; }
    @GetMapping
    public ResponseEntity<CuentaDestinoService.Destino> buscar(
            @RequestParam @Pattern(regexp = "[0-9 -]{6,34}") String numero, HttpSession session) {
        return ResponseEntity.of(cuentas.buscar(numero, (String) session.getAttribute("codigoCliente")));
    }
}
