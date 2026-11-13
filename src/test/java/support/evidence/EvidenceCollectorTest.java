package support.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceCollectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @AfterEach
    void clearCapturedScenarios() {
        EvidenceCollector.reset();
    }

    @Test
    void capturesKarateExecutionAndRedactsEvidenceBeforeItIsStored() {
        EvidenceCollector.reset();
        EvidenceCollector.capture("""
                {
                  "scenarioName": "Return the registered users",
                  "scenarioDescription": "Read-only users smoke check",
                  "featureFile": "src/test/java/features/users/list-users.feature",
                  "tags": ["@id=USR-GET-001", "@priority=High", "@type=Smoke-Contract"],
                  "environment": "qa",
                  "errorMessage": "",
                  "request": {
                    "method": "GET",
                    "url": "https://qa.example.test/users",
                    "headers": {"Authorization": "Bearer test-secret", "Accept": "application/json"},
                    "payload": null
                  },
                  "response": {
                    "status": 200,
                    "headers": {"Content-Type": ["application/json"]},
                    "body": [{"id": 7, "name": "Synthetic Person", "email": "person@example.test"}],
                    "durationMs": 18
                  }
                }
                """);

        List<Map<String, Object>> scenarios = EvidenceCollector.snapshot();
        assertEquals(1, scenarios.size());
        Map<String, Object> scenario = scenarios.get(0);
        assertEquals("USR-GET-001", scenario.get("id"));
        assertEquals("PASS", scenario.get("status"));
        assertTrue(String.valueOf(scenario.get("gherkin")).contains("Funcionalidade: List users"));
        assertTrue(String.valueOf(scenario.get("gherkin")).contains("Então status 200"));
        assertEquals(18, ((Map<?, ?>) scenario.get("response")).get("durationMs"));
        assertEquals("[REDACTED]", ((Map<?, ?>) ((List<?>) ((Map<?, ?>) scenario.get("response"))
                .get("body")).get(0)).get("email"));
        assertEquals("[REDACTED]", ((Map<?, ?>) ((Map<?, ?>) scenario.get("request"))
                .get("headers")).get("Authorization"));
    }

    @Test
    void decodesKarateByteArrayPayloadBeforeRedactingIt() throws Exception {
        String requestBody = "{\"name\":\"Synthetic Person\",\"email\":\"qa@example.test\",\"password\":\"test-secret\"}";
        List<Integer> bytes = new ArrayList<>();
        for (byte value : requestBody.getBytes(StandardCharsets.UTF_8)) {
            bytes.add((int) value);
        }
        String capture = """
                {
                  "scenarioName": "Cadastrar usuário com dados válidos",
                  "featureFile": "src/test/java/features/users/cadastro-usuario.feature",
                  "tags": ["@id=USR-POST-TEST"],
                  "request": {
                    "method": "POST",
                    "url": "https://qa.example.test/users",
                    "headers": {"Content-Type": "application/json"},
                    "payload": %s
                  },
                  "response": {"status": 201, "body": {"ok": true}, "durationMs": 12}
                }
                """.formatted(MAPPER.writeValueAsString(bytes));

        EvidenceCollector.capture(capture);

        Map<String, Object> scenario = EvidenceCollector.snapshot().get(0);
        Map<?, ?> payload = (Map<?, ?>) ((Map<?, ?>) scenario.get("request")).get("payload");
        assertEquals("[REDACTED]", payload.get("name"));
        assertEquals("[REDACTED]", payload.get("email"));
        assertEquals("[REDACTED]", payload.get("password"));
    }

    @Test
    void localizesKarateGherkinStepsForPortugueseEvidence() {
        EvidenceCollector.capture("""
                {
                  "scenarioName": "Cadastrar usuário com dados válidos",
                  "featureFile": "src/test/java/features/users/cadastro-usuario.feature",
                  "tags": ["@id=USR-POST-008"],
                  "environment": "qa",
                  "request": {"method": "POST", "url": "https://qa.example.test/users"},
                  "response": {"status": 201}
                }
                """);

        Map<String, Object> scenario = EvidenceCollector.snapshot().get(0);
        String gherkin = String.valueOf(scenario.get("gherkin"));
        assertTrue(gherkin.contains("Funcionalidade: Cadastro de usuários"));
        assertTrue(gherkin.contains("Cenário: Cadastrar usuário com dados válidos"));
        assertTrue(gherkin.contains("Então status 201"));
        assertEquals("QA / Teste", scenario.get("environment"));
        assertEquals("Aprovado", scenario.get("statusLabel"));
    }

    @Test
    void localizesScenarioOutlineWithItsResolvedExampleValues() {
        EvidenceCollector.capture("""
                {
                  "scenarioName": "Rejeitar login para e-mail malformado (esperado HTTP 400)",
                  "featureFile": "src/test/java/features/auth/login.feature",
                  "tags": ["@id=AUTH-LOGIN-005"],
                  "environment": "qa",
                  "request": {"method": "POST", "url": "https://qa.example.test/auth/login"},
                  "response": {"status": 400}
                }
                """);

        String gherkin = String.valueOf(EvidenceCollector.snapshot().get(0).get("gherkin"));
        assertTrue(gherkin.contains("Cenário: Rejeitar login para e-mail malformado (esperado HTTP 400)"));
        assertTrue(gherkin.contains("Então status 400"));
        assertFalse(gherkin.contains("<caso>"));
        assertFalse(gherkin.contains("Examples:"));
    }

    @Test
    void localizesReusableUserSetupFeatureForEvidence() {
        EvidenceCollector.capture("""
                {
                  "scenarioName": "Criar usuário novo para os cenários de login",
                  "featureFile": "src/test/java/support/evidence/create-login-user.feature",
                  "tags": ["@id=AUTH-SETUP-001"],
                  "environment": "qa",
                  "request": {"method": "POST", "url": "https://qa.example.test/users"},
                  "response": {"status": 201}
                }
                """);

        String gherkin = String.valueOf(EvidenceCollector.snapshot().get(0).get("gherkin"));
        assertTrue(gherkin.contains("Funcionalidade: Preparação reutilizável de usuário sintético para autenticação"));
        assertTrue(gherkin.contains("Cenário: Criar usuário novo para os cenários de login"));
        assertTrue(gherkin.contains("Então status 201"));
    }
}
