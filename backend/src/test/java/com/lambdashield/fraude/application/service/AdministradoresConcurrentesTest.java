package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.command.UsuarioActualizarCommand;
import com.lambdashield.fraude.application.command.UsuarioCrearCommand;
import com.lambdashield.fraude.domain.enums.EstadoUsuario;
import com.lambdashield.fraude.repository.UsuarioSistemaRepository;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:administradores;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdministradoresConcurrentesTest {
    @Autowired UsuarioAdminService servicio;
    @Autowired UsuarioSistemaRepository usuarios;
    @Autowired RolUsuarioService roles;
    private List<Long> ids;

    @BeforeEach
    void prepararDosAdministradores() {
        long primero = usuarios.findByNombreUsuario("admin").orElseThrow().getIdUsuario();
        long segundo = servicio.crear(new UsuarioCrearCommand("admin.segundo", "Segundo Admin",
                "segundo@lambda.local", "ClaveSegura123", "ADMIN", null)).id();
        ids = List.of(primero, segundo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bloquear", "degradar", "eliminar", "mixto"})
    void dosSolicitudesConservanUnAdministradorActivo(String operacion) throws Exception {
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            var tareas = ids.stream().map(id -> ejecutor.submit(() -> {
                listos.countDown();
                if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Sin inicio");
                try {
                    if (operacion.equals("eliminar") || operacion.equals("mixto") && id.equals(ids.getFirst())) {
                        servicio.eliminar(id, "operador-de-prueba");
                    } else {
                        var actual = servicio.buscar(id);
                        servicio.actualizar(id, new UsuarioActualizarCommand(actual.nombres(), actual.correo(),
                                operacion.equals("degradar") ? "ACTIVO" : "BLOQUEADO",
                                operacion.equals("degradar") ? "ANALISTA" : "ADMIN", null));
                    }
                    return true;
                } catch (IllegalArgumentException esperado) {
                    assertThat(esperado).hasMessageContaining("último administrador");
                    return false;
                }
            })).toList();
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
            inicio.countDown();
            assertThat(List.of(tareas.get(0).get(15, TimeUnit.SECONDS), tareas.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(usuarios.findAll().stream().filter(u -> u.getEstadoUsuario() == EstadoUsuario.ACTIVO
                    && "ADMIN".equals(roles.codigo(u.getIdUsuario()))).count()).isEqualTo(1);
        } finally {
            inicio.countDown();
        }
    }
}
