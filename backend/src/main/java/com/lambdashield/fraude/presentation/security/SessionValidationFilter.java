package com.lambdashield.fraude.presentation.security;

import com.lambdashield.fraude.application.service.AuthService;
import com.lambdashield.fraude.application.service.AuthService.UsuarioSesion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/** Revoca sesiones al bloquear/eliminar al usuario o cambiar su clave; actualiza rol y cliente. */
public class SessionValidationFilter extends OncePerRequestFilter {
    private final AuthService auth;
    public SessionValidationFilter(AuthService auth) { this.auth = auth; }

    public static UsernamePasswordAuthenticationToken authentication(UsuarioSesion usuario) {
        return UsernamePasswordAuthenticationToken.authenticated(usuario, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol())));
    }

    public static void actualizarSesion(HttpSession session, UsuarioSesion usuario) {
        session.setAttribute("usuario", usuario.usuario());
        session.setAttribute("nombreUsuario", usuario.nombreCompleto());
        session.setAttribute("rol", usuario.rol());
        if (usuario.codigoCliente() == null) session.removeAttribute("codigoCliente");
        else session.setAttribute("codigoCliente", usuario.codigoCliente());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var actual = SecurityContextHolder.getContext().getAuthentication();
        if (actual != null && actual.getPrincipal() instanceof UsuarioSesion anterior) {
            var vigente = auth.obtenerSesionActiva(anterior.usuario())
                    .filter(u -> u.credencial().equals(anterior.credencial()));
            if (vigente.isEmpty()) {
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
            } else {
                var contexto = SecurityContextHolder.createEmptyContext();
                contexto.setAuthentication(authentication(vigente.get()));
                SecurityContextHolder.setContext(contexto);
                actualizarSesion(request.getSession(), vigente.get());
            }
        } else if (actual != null) {
            // Solo se admiten identidades autenticadas por el proveedor de la aplicación.
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
