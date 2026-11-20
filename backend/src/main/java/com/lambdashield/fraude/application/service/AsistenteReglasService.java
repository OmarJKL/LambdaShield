package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.model.DefinicionRegla;
import com.lambdashield.fraude.application.model.RevisionRegla;
import org.springframework.stereotype.Service;

@Service
public class AsistenteReglasService {
    private final AsistenteReglasPort asistente;
    public AsistenteReglasService(AsistenteReglasPort asistente) { this.asistente = asistente; }
    public boolean disponible() { return asistente.disponible(); }
    public String configuracion() { return asistente.configuracion(); }
    public RevisionRegla revisar(DefinicionRegla definicion) {
        definicion.validar();
        return asistente.revisar(definicion);
    }
}
