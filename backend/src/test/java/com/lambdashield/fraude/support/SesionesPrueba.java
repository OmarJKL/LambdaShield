package com.lambdashield.fraude.support;

import com.lambdashield.fraude.application.service.AuthService;
import com.lambdashield.fraude.presentation.security.SessionValidationFilter;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Identidades reales del fixture; nunca se desactivan los filtros de seguridad. */
public final class SesionesPrueba {
    private SesionesPrueba() {}
    public static MockHttpSession crear(AuthService auth, String rol) {
        String nombre = switch (rol) { case "ADMIN" -> "admin"; case "ANALISTA" -> "analista"; default -> "usuario"; };
        var usuario = auth.obtenerSesionActiva(nombre).orElseThrow();
        MockHttpSession sesion = new MockHttpSession();
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(SessionValidationFilter.authentication(usuario));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        SessionValidationFilter.actualizarSesion(sesion, usuario);
        return sesion;
    }
    public static RequestPostProcessor csrf() {
        return request -> {
            var repositorio = new HttpSessionCsrfTokenRepository();
            CsrfToken existente = repositorio.loadToken(request);
            if (existente == null) {
                existente = repositorio.generateToken(request);
                repositorio.saveToken(existente, request, new MockHttpServletResponse());
            }
            CsrfToken token = existente;
            new XorCsrfTokenRequestAttributeHandler().handle(request, new MockHttpServletResponse(), () -> token);
            CsrfToken enmascarado = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            request.addHeader(enmascarado.getHeaderName(), enmascarado.getToken());
            return request;
        };
    }
}
