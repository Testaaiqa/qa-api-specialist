package support.evidence;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceReportGeneratorTest {

    @Test
    void rendersHtmlToPdfInMemory() throws Exception {
        byte[] pdf = EvidenceReportGenerator.renderPdf(
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><h1>Evidence test</h1></body></html>",
                "file:/"
        );

        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    @Test
    void rendersEachResponseDirectlyBelowItsRequest() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("project", "QA API Specialist");
        metadata.put("documentTitle", "RELATÓRIO DE EVIDÊNCIAS DE TESTES DE API");
        metadata.put("demandId", "TEST");
        metadata.put("executionId", "EXEC-TEST");
        metadata.put("documentVersion", "1.0");
        metadata.put("projectVersion", "1.0");
        metadata.put("environment", "QA / Teste");
        metadata.put("apiUrl", "https://qa.example.test");
        metadata.put("analyst", "Analista");
        metadata.put("framework", "Karate DSL");
        metadata.put("startedAt", "2026-01-01T10:00:00Z");
        metadata.put("endedAt", "2026-01-01T10:01:00Z");
        metadata.put("timezone", "UTC");
        metadata.put("objective", "Validar cadastro");
        metadata.put("origin", "Demanda de teste");
        metadata.put("functionalDescription", "Cadastro de usuário");
        metadata.put("businessRules", "Campos obrigatórios");
        metadata.put("acceptanceCriteria", "HTTP 201");
        metadata.put("dependencies", "Nenhuma");
        metadata.put("scope", "POST /users");
        metadata.put("outOfScope", "Exclusão");

        Map<String, Object> request = Map.of(
                "url", "https://qa.example.test/users",
                "method", "POST",
                "headers", Map.of("Content-Type", "application/json"),
                "contentType", "application/json",
                "correlationId", "",
                "payload", Map.of("name", "Synthetic", "password", "synthetic-password")
        );
        Map<String, Object> response = Map.of(
                "status", 201,
                "headers", Map.of("Content-Type", "application/json"),
                "body", Map.of("id", 1),
                "durationMs", 12
        );
        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("id", "USR-POST-001");
        scenario.put("name", "Cadastrar usuário");
        scenario.put("description", "Cadastro positivo");
        scenario.put("gherkin", "Funcionalidade: Cadastro\nCenário: Cadastrar usuário\nEntão status 201");
        scenario.put("priority", "Alta");
        scenario.put("category", "Contrato");
        scenario.put("request", request);
        scenario.put("response", response);
        scenario.put("startedAt", "2026-01-01T10:00:00Z");
        scenario.put("receivedAt", "2026-01-01T10:00:01Z");
        scenario.put("status", "PASS");
        scenario.put("statusLabel", "Aprovado");
        scenario.put("blockedReason", "");
        scenario.put("featureFile", "features/users/cadastro-usuario.feature");

        String html = EvidenceReportGenerator.renderHtml(
                metadata,
                Map.of("planned", 1, "executed", 1, "passed", 1, "failed", 0,
                        "blocked", 0, "notExecuted", 0, "passRatePercentOfExecuted", 100.0),
                "APROVADO",
                List.of(),
                List.of(scenario)
        );

        int requestIndex = html.indexOf("<h4>Requisição</h4>");
        int responseIndex = html.indexOf("<h4>Resposta da API</h4>");
        assertTrue(requestIndex >= 0);
        assertTrue(responseIndex > requestIndex);
        assertTrue(html.indexOf("Payload enviado", requestIndex) < responseIndex);
        assertTrue(html.indexOf("Body da resposta", responseIndex) > responseIndex);
    }
}
