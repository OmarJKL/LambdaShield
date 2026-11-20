package com.lambdashield.fraude.presentation.security;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;

class RateLimitWindowTest {
    @Test
    void asistenteTieneLimitePropioSinBloquearLasConsultasHabituales() throws Exception {
        var filtro = new RequestRateLimitFilter(100, 100, 100);
        for (int i = 0; i < 7; i++) {
            var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/rule/assistant/review");
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            filtro.doFilter(request, response, (req, res) -> {});
            assertThat(response.getStatus()).isEqualTo(i < 6 ? 200 : 429);
        }
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        filtro.doFilter(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/rule"), response, (req, res) -> {});
        assertThat(response.getStatus()).isEqualTo(200);
    }
    @Test
    void limitaIdentidadesPorSeparadoYRenuevaLaVentana() {
        AtomicLong ahora = new AtomicLong(0);
        Clock reloj = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zona) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(ahora.get()); }
        };
        var ventana = new RequestRateLimitFilter.Ventana(2, reloj);
        assertThat(ventana.admitir("a")).isTrue();
        assertThat(ventana.admitir("a")).isTrue();
        assertThat(ventana.admitir("a")).isFalse();
        assertThat(ventana.admitir("b")).isTrue();
        ahora.set(60_000);
        assertThat(ventana.admitir("a")).isTrue();
    }
}
