package com.lambdashield.fraude.config.seed;

import java.math.BigDecimal;
import java.util.List;

/** Datos ficticios públicos, destinados exclusivamente al conjunto de demostración. */
public final class DatasetDemo {
    private DatasetDemo() {}
    public record Persona(int numero, String nombres, String paterno, String materno,
                          String usuario, String clave, BigDecimal apertura, BigDecimal haberes, BigDecimal promedio) {
        public String codigo() { return "D-" + numero; }
        public String cuenta() { return "0011-2026-0000000" + numero; }
        public String correo() { return usuario + "@example.test"; }
        public String nombreCompleto() { return nombres + " " + paterno + " " + materno; }
    }
    private static Persona persona(int numero, String nombres, String paterno, String materno,
                                    String usuario, String clave, String apertura, String haberes, String promedio) {
        return new Persona(numero, nombres, paterno, materno, usuario, clave,
                new BigDecimal(apertura), new BigDecimal(haberes), new BigDecimal(promedio));
    }
    public static final List<Persona> PERSONAS = List.of(
            persona(101, "Lucía", "Vargas", "Mora", "demo.lucia", "DemoLucia!2026", "8500.00", "2200.00", "350.00"),
            persona(102, "Carlos", "Rojas", "Díaz", "demo.carlos", "DemoCarlos!2026", "12500.00", "3600.00", "650.00"),
            persona(103, "Ana", "Torres", "Vega", "demo.ana", "DemoAna!2026", "6800.00", "2800.00", "450.00"),
            persona(104, "Jorge", "Salas", "Mendoza", "demo.jorge", "DemoJorge!2026", "18000.00", "4200.00", "900.00"),
            persona(105, "Valeria", "López", "Cruz", "demo.valeria", "DemoValeria!2026", "4200.00", "1800.00", "300.00"),
            persona(106, "Diego", "Castro", "León", "demo.diego", "DemoDiego!2026", "9600.00", "3200.00", "550.00"),
            persona(107, "Sofía", "Mendoza", "Ríos", "demo.sofia", "DemoSofia!2026", "14500.00", "4000.00", "750.00"),
            persona(108, "Mateo", "Ruiz", "Flores", "demo.mateo", "DemoMateo!2026", "3500.00", "1600.00", "250.00")
    );
    public static final String ADMIN = "demo.admin";
    public static final String CLAVE_ADMIN = "DemoAdmin!2026";
    public static final String ANALISTA = "demo.analista";
    public static final String CLAVE_ANALISTA = "DemoAnalista!2026";
}
