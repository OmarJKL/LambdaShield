package com.lambdashield.fraude.config.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Activación explícita e independiente de los antiguos datos demo. */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.demo.dataset.enabled", havingValue = "true")
public class DatasetDemoRunner implements CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(DatasetDemoRunner.class);
    private final DatasetDemoLoader loader;
    private final ConfigurableApplicationContext contexto;
    private final boolean salir;
    public DatasetDemoRunner(DatasetDemoLoader loader, ConfigurableApplicationContext contexto,
                             @Value("${app.demo.dataset.exit-after-load:false}") boolean salir) {
        this.loader = loader;
        this.contexto = contexto;
        this.salir = salir;
    }
    @Override public void run(String... args) {
        var resumen = loader.cargar();
        LOG.info("Conjunto demo listo: {} clientes, {} cuentas, {} usuarios y {} transacciones nuevas",
                resumen.clientes(), resumen.cuentas(), resumen.usuarios(), resumen.transacciones());
    }
    @EventListener(ApplicationReadyEvent.class)
    public void alTerminarArranque() {
        // ApplicationReadyEvent ocurre después de confirmar la transacción del loader.
        if (salir) SpringApplication.exit(contexto, () -> 0);
    }
}
