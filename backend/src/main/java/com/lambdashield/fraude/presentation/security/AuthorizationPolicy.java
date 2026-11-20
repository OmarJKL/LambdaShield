package com.lambdashield.fraude.presentation.security;

import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.Set;

/** Lista explícita de capacidades; rutas nuevas quedan denegadas hasta declararlas. */
@Component
public class AuthorizationPolicy {
    private static final Set<String> LECTURAS = Set.of("/api/v1/evaluation", "/api/v1/metric",
            "/api/v1/metric/summary", "/api/v1/metric/movement", "/api/v1/metric/recent",
            "/api/v1/rule", "/api/v1/report/export");
    private static final Set<String> PAGINAS = Set.of("/", "/transacciones", "/reglas", "/reportes");

    public Optional<String> rechazoApi(String rol, String metodo, String ruta) {
        boolean admin = "ADMIN".equals(rol);
        boolean lector = admin || "ANALISTA".equals(rol);
        boolean usuario = ruta.equals("/api/v1/user") || ruta.matches("/api/v1/user/-?[0-9]+(?:/clave)?");
        if (ruta.equals("/api/v1/rule/assistant/status") && Set.of("GET", "HEAD").contains(metodo)
                || ruta.equals("/api/v1/rule/assistant/review") && "POST".equals(metodo)) {
            return admin ? Optional.empty() : Optional.of("No autorizado");
        }
        if (ruta.equals("/api/v1/account/destination") && Set.of("GET", "HEAD").contains(metodo)) {
            return "CLIENTE".equals(rol) ? Optional.empty() : Optional.of("No autorizado");
        }
        if (ruta.equals("/api/v1/notification") && Set.of("GET", "HEAD").contains(metodo)
                || ruta.matches("/api/v1/notification/-?[0-9]+/leer") && "PATCH".equals(metodo)
                || ruta.equals("/api/v1/notification/leer-todas") && "PATCH".equals(metodo)) {
            return "CLIENTE".equals(rol) ? Optional.empty() : Optional.of("No autorizado");
        }
        boolean permitido = switch (metodo) {
            case "GET", "HEAD" -> lector && LECTURAS.contains(ruta)
                    || admin && (ruta.equals("/api/v1/user") || ruta.matches("/api/v1/user/-?[0-9]+"));
            case "POST" -> ruta.equals("/api/v1/evaluation") && (admin || "CLIENTE".equals(rol))
                    || admin && ruta.equals("/api/v1/user");
            case "PATCH" -> admin && (ruta.matches("/api/v1/evaluation/-?[0-9]+(?:/activar)?")
                    || usuario && !ruta.equals("/api/v1/user")
                    || ruta.matches("/api/v1/rule/[A-Za-z0-9_-]+/estado"));
            case "DELETE" -> admin && ruta.matches("/api/v1/user/-?[0-9]+");
            default -> false;
        };
        return permitido ? Optional.empty() : Optional.of("No autorizado");
    }

    public Optional<String> redireccionPagina(String rol, String ruta) {
        if ("ADMIN".equals(rol) && (PAGINAS.contains(ruta) || Set.of("/usuarios", "/simulador", "/operacion").contains(ruta))) return Optional.empty();
        if ("ANALISTA".equals(rol)) return PAGINAS.contains(ruta) ? Optional.empty() : Optional.of("/");
        if ("CLIENTE".equals(rol)) return "/operacion".equals(ruta) ? Optional.empty() : Optional.of("/operacion");
        return Optional.of("/login");
    }
}
