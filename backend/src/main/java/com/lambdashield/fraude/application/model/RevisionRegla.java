package com.lambdashield.fraude.application.model;

import java.util.List;

public record RevisionRegla(String interpretacion, List<String> advertencias, List<String> sugerencias,
                           List<String> pruebas) {}
