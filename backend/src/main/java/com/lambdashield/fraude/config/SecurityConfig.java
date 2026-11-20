package com.lambdashield.fraude.config;

import com.lambdashield.fraude.application.service.AuthService;
import com.lambdashield.fraude.application.service.AuthService.UsuarioSesion;
import com.lambdashield.fraude.presentation.security.AuthorizationPolicy;
import com.lambdashield.fraude.presentation.security.RequestRateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import com.lambdashield.fraude.presentation.security.SessionValidationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    AuthenticationProvider authenticationProvider(AuthService auth) {
        return new AuthenticationProvider() {
            @Override public Authentication authenticate(Authentication intento) {
                var usuario = auth.autenticar(intento.getName(), String.valueOf(intento.getCredentials()))
                        .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
                return SessionValidationFilter.authentication(usuario);
            }
            @Override public boolean supports(Class<?> tipo) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
            }
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService auth, AuthorizationPolicy policy,
                                            AuthenticationProvider proveedor,
                                            @Value("${security.rate-limit.login-per-minute:10}") int logins,
                                            @Value("${security.rate-limit.api-per-minute:120}") int peticiones,
                                            @Value("${security.rate-limit.writes-per-minute:40}") int cambios,
                                            @Value("${security.rate-limit.assistant-per-minute:6}") int revisiones) throws Exception {
        http.authenticationProvider(proveedor)
                .addFilterBefore(new SessionValidationFilter(auth), CsrfFilter.class)
                .addFilterBefore(new RequestRateLimitFilter(logins, peticiones, cambios, revisiones), SessionValidationFilter.class)
                .authorizeHttpRequests(reglas -> reglas
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/login", "/actuator/health", "/css/**", "/js/**", "/img/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/login").permitAll()
                        .requestMatchers("/api/**").access((identidad, contexto) -> new AuthorizationDecision(
                                policy.rechazoApi(rol(identidad.get()), contexto.getRequest().getMethod(), ruta(contexto.getRequest())).isEmpty()))
                        .requestMatchers("/", "/operacion", "/simulador", "/usuarios", "/reportes", "/reglas", "/transacciones")
                        .access((identidad, contexto) -> new AuthorizationDecision(
                                policy.redireccionPagina(rol(identidad.get()), ruta(contexto.getRequest())).isEmpty()))
                        .anyRequest().denyAll())
                .formLogin(login -> login.loginPage("/login").usernameParameter("usuario").passwordParameter("clave")
                        .successHandler((request, response, identidad) -> {
                            UsuarioSesion usuario = (UsuarioSesion) identidad.getPrincipal();
                            SessionValidationFilter.actualizarSesion(request.getSession(), usuario);
                            response.sendRedirect(request.getContextPath() + ("CLIENTE".equals(usuario.rol()) ? "/operacion" : "/"));
                        }).failureUrl("/login?error"))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login").invalidateHttpSession(true).deleteCookies("JSESSIONID"))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((request, response, ex) -> {
                            if (esApi(request)) json(response, 401, "No autenticado");
                            else if (ruta(request).startsWith("/actuator/")) response.sendError(404);
                            else response.sendRedirect(request.getContextPath() + "/login");
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            if (ex instanceof CsrfException) json(response, 403, "Token CSRF ausente o inválido");
                            else if (esApi(request)) json(response, 403, "No autorizado");
                            else {
                                var identidad = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                                var destino = policy.redireccionPagina(rol(identidad), ruta(request));
                                if (destino.isPresent() && !destino.get().equals(ruta(request))) response.sendRedirect(request.getContextPath() + destino.get());
                                else response.sendError(403);
                            }
                        }))
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; font-src 'self' https://cdn.jsdelivr.net https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")));
        return http.build();
    }

    private static String rol(Authentication identidad) {
        return identidad != null && identidad.getPrincipal() instanceof UsuarioSesion usuario ? usuario.rol() : null;
    }
    private static String ruta(HttpServletRequest request) { return request.getRequestURI().substring(request.getContextPath().length()); }
    private static boolean esApi(HttpServletRequest request) { return ruta(request).equals("/api") || ruta(request).startsWith("/api/"); }
    private static void json(HttpServletResponse response, int estado, String error) throws IOException {
        response.setStatus(estado);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
