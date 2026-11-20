package com.lambdashield.fraude.presentation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/** Límites por IP para login y por identidad para API. No confía en X-Forwarded-For. */
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private final Ventana login;
    private final Ventana api;
    private final Ventana escrituras;
    private final Ventana asistente;
    public RequestRateLimitFilter(int logins, int peticiones, int cambios) {
        this(logins, peticiones, cambios, 6);
    }
    public RequestRateLimitFilter(int logins, int peticiones, int cambios, int revisiones) {
        asistente = new Ventana(revisiones, Clock.systemUTC());
        login = new Ventana(logins, Clock.systemUTC());
        api = new Ventana(peticiones, Clock.systemUTC());
        escrituras = new Ventana(cambios, Clock.systemUTC());
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        String metodo = request.getMethod();
        boolean permitido = true;
        if ("/login".equals(ruta) && "POST".equals(metodo)) {
            permitido = login.admitir(request.getRemoteAddr());
        } else if (ruta.equals("/api") || ruta.startsWith("/api/")) {
            var identidad = SecurityContextHolder.getContext().getAuthentication();
            String clave = identidad != null ? "usuario:" + identidad.getName() : "ip:" + request.getRemoteAddr();
            permitido = api.admitir(clave);
            if (permitido && ruta.equals("/api/v1/rule/assistant/review") && "POST".equals(metodo)) {
                permitido = asistente.admitir(clave);
            }
            if (permitido && !"GET".equals(metodo) && !"HEAD".equals(metodo) && !"OPTIONS".equals(metodo)) {
                permitido = escrituras.admitir(clave);
            }
        }
        if (!permitido) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Demasiadas solicitudes; inténtalo de nuevo en un minuto\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    static final class Ventana {
        private record Uso(long minuto, int cantidad) {}
        private final Map<String, Uso> usos = new LinkedHashMap<>(128, 0.75f, true);
        private final int limite;
        private final Clock reloj;
        Ventana(int limite, Clock reloj) {
            if (limite < 1) throw new IllegalArgumentException("El límite debe ser positivo");
            this.limite = limite;
            this.reloj = reloj;
        }
        synchronized boolean admitir(String clave) {
            long minuto = reloj.millis() / 60_000;
            Uso uso = usos.get(clave);
            if (uso != null && uso.minuto() == minuto && uso.cantidad() >= limite) return false;
            // No se permite crecimiento ilimitado de memoria ni expulsar ventanas activas.
            if (uso == null && usos.size() >= 10_000) {
                usos.values().removeIf(valor -> valor.minuto() != minuto);
                if (usos.size() >= 10_000) return false;
            }
            usos.put(clave, new Uso(minuto, uso != null && uso.minuto() == minuto ? uso.cantidad() + 1 : 1));
            return true;
        }
    }
}
