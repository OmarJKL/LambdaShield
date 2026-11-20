package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.service.NotificacionService;
import com.lambdashield.fraude.presentation.dto.response.NotificacionResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notification")
public class NotificationApiController {
    public record BandejaResponse(List<NotificacionResponse> items, long noLeidas) {}
    private final NotificacionService servicio;
    public NotificationApiController(NotificacionService servicio) { this.servicio = servicio; }
    @GetMapping
    public BandejaResponse listar(Principal principal, @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int pagina) {
        var bandeja = servicio.listar(principal.getName(), pagina);
        return new BandejaResponse(bandeja.items().stream().map(n -> new NotificacionResponse(n.id(), n.tipo(), n.titulo(),
                n.mensaje(), n.operacion(), n.monto(), n.puntaje(), n.fecha(), n.leida())).toList(), bandeja.noLeidas());
    }
    @PatchMapping("/{id}/leer")
    public ResponseEntity<Void> leer(Principal principal, @PathVariable @Positive Long id) {
        servicio.leer(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/leer-todas")
    public ResponseEntity<Void> leerTodas(Principal principal) {
        servicio.leerTodas(principal.getName());
        return ResponseEntity.noContent().build();
    }
}
