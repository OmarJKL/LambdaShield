package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.model.DefinicionRegla;
import com.lambdashield.fraude.application.model.RevisionRegla;

public interface AsistenteReglasPort {
    boolean disponible();
    default String configuracion() { return disponible() ? "Configurado" : "Configuración pendiente"; }
    RevisionRegla revisar(DefinicionRegla definicion);
}
