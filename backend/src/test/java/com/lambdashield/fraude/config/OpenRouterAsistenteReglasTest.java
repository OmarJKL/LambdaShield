package com.lambdashield.fraude.config;

import com.lambdashield.fraude.application.model.DefinicionRegla;
import com.lambdashield.fraude.application.service.AsistenteReglasException;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OpenRouterAsistenteReglasTest {
    private static final String URL = "https://openrouter.ai/api/v1";
    private static final String REVISION = """
        {"interpretacion":"Se requieren ambas condiciones.","advertencias":["El promedio no tiene periodo calculado."],
        "sugerencias":[],"pruebas":["Monto superior: dispara.","Monto igual al límite: no dispara."]}
        """;
    private final HttpClient http = mock(HttpClient.class);
    private OpenRouterAsistenteReglas adaptador(String url) { return new OpenRouterAsistenteReglas(true, "clave-ficticia", url, "nvidia/nemotron-3.5-super-vl:free", 1, http); }
    private DefinicionRegla definicion() {
        return new DefinicionRegla("Regla", "Revisar", DefinicionRegla.Combinacion.TODAS, 25,
                List.of(new DefinicionRegla.Condicion(DefinicionRegla.Campo.MONTO, DefinicionRegla.Operador.MAYOR, "1000")));
    }
    @SuppressWarnings("unchecked")
    private void response(int status, String body) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(response));
    }
    @Test void aceptaSoloOpenRouterHttpsSinCredencialesEnLaUrl() {
        assertThat(adaptador(URL).disponible()).isTrue();
        for (String url : List.of("http://openrouter.ai/api/v1", "https://evil.test/api/v1",
                "https://openrouter.ai.evil.test/api/v1", "https://user:pass@openrouter.ai/api/v1",
                "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", "https://openrouter.ai:444/api/v1",
                "https://openrouter.ai/api/v1?key=x", "https://openrouter.ai/api/v1#fragment",
                "https://openrouter.ai/other/api/v1", "https://openrouter.ai/api/v1/chat/completions")) {
            assertThat(adaptador(url).disponible()).isFalse();
            assertThatThrownBy(() -> adaptador(url).revisar(definicion())).isInstanceOf(AsistenteReglasException.class);
        }
        verifyNoInteractions(http);
    }
    @Test void validaRespuestaSinAceptarCodigoCamposExtraOTiposIncorrectos() {
        var adapter = adaptador(URL);
        assertThat(adapter.interpretar(REVISION).pruebas()).hasSize(2);
        for (String invalid : List.of("no-json", REVISION + "{}",
                REVISION.replace("\"sugerencias\":[]", "\"sugerencias\":[],\"sugerencias\":[]"),
                REVISION.replace("\"sugerencias\":[]", "\"sugerencias\":[],\"codigo\":\"ejecutar\""),
                REVISION.replace("\"sugerencias\":[]", "\"sugerencias\":\"texto\""), REVISION.replace("Se requieren ambas condiciones.", ""))) {
            assertThatThrownBy(() -> adapter.interpretar(invalid)).isInstanceOf(AsistenteReglasException.class);
        }
    }
    @Test void traduceErroresDelProveedorSinFiltrarClavesNiCuerpos() {
        for (int status : List.of(401, 402, 403, 429, 500)) {
            response(status, "clave-secreta-y-detalle-interno");
            assertThatThrownBy(() -> adaptador(URL).revisar(definicion())).isInstanceOf(AsistenteReglasException.class)
                    .hasMessageNotContaining("clave-secreta-y-detalle-interno");
        }
        response(402, "detalle-interno");
        assertThatThrownBy(() -> adaptador(URL).revisar(definicion())).hasMessageContaining("créditos")
                .hasMessageNotContaining("detalle-interno");
    }
    @Test void solicitudRealTieneEndpointYSalidaJsonAcotada() throws Exception {
        String encoded = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(
                java.util.Map.of("choices", List.of(java.util.Map.of("finish_reason", "stop", "message", java.util.Map.of("content", REVISION)))));
        response(200, encoded);
        assertThat(adaptador(URL).revisar(definicion()).interpretacion()).contains("ambas");
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString()).isEqualTo(URL + "/chat/completions");
        assertThat(request.getValue().headers().firstValue("Authorization")).contains("Bearer clave-ficticia");
        var bytes = new java.io.ByteArrayOutputStream();
        var received = new CompletableFuture<Void>();
        request.getValue().bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(java.nio.ByteBuffer buffer) {
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            public void onError(Throwable error) { received.completeExceptionally(error); }
            public void onComplete() { received.complete(null); }
        });
        received.get(1, java.util.concurrent.TimeUnit.SECONDS);
        var payload = tools.jackson.databind.json.JsonMapper.builder().build().readTree(bytes.toByteArray());
        assertThat(payload.path("model").asText()).isEqualTo("nvidia/nemotron-3.5-super-vl:free");
        assertThat(payload.path("reasoning").path("enabled").asBoolean()).isFalse();
        assertThat(payload.has("enable_thinking")).isFalse();
        assertThat(payload.path("response_format").path("type").asText()).isEqualTo("json_schema");
        var esquema = payload.path("response_format").path("json_schema");
        assertThat(esquema.path("strict").asBoolean()).isTrue();
        assertThat(esquema.path("schema").path("additionalProperties").asBoolean()).isFalse();
        assertThat(esquema.path("schema").path("required")).hasSize(4);
        assertThat(esquema.path("schema").path("properties").path("pruebas").path("minItems").asInt()).isEqualTo(2);
        assertThat(payload.path("max_tokens").asInt()).isEqualTo(1600);
    }
    @Test void salidaTruncadaNoSeUsaComoRevision() {
        response(200, "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}");
        assertThatThrownBy(() -> adaptador(URL).revisar(definicion())).isInstanceOf(AsistenteReglasException.class);
    }
    @Test void falloDeRedOTiempoDeEsperaNoProduceRevisionInventada() {
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(new java.io.IOException("clave-secreta")));
        assertThatThrownBy(() -> adaptador(URL).revisar(definicion())).isInstanceOf(AsistenteReglasException.class).hasMessageNotContaining("clave-secreta");
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(new CompletableFuture<>());
        assertThatThrownBy(() -> adaptador(URL).revisar(definicion())).isInstanceOf(AsistenteReglasException.class);
    }
}
