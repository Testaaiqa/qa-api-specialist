package support.evidence;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EvidenceRedactorTest {

    @Test
    void redactsCredentialAndPersonalFieldsWithoutChangingOtherValues() {
        Object safe = EvidenceRedactor.redact(Map.of(
                "password", "dont-print-this",
                "email", "person@example.com",
                "name", "Synthetic Person",
                "active", true,
                "id", 42
        ));

        assertEquals("[REDACTED]", ((Map<?, ?>) safe).get("password"));
        assertEquals("[REDACTED]", ((Map<?, ?>) safe).get("email"));
        assertEquals("[REDACTED]", ((Map<?, ?>) safe).get("name"));
        assertEquals(true, ((Map<?, ?>) safe).get("active"));
        assertEquals(42, ((Map<?, ?>) safe).get("id"));
    }

    @Test
    void revealsSyntheticPersonalDataOnlyWhenRequestedAndAlwaysMasksCredentials() {
        Object safe = EvidenceRedactor.redact(Map.of(
                "password", "test-password",
                "accessToken", "test-token",
                "email", "qa@example.test",
                "name", "Synthetic Person"
        ), true);

        assertEquals("[REDACTED]", ((Map<?, ?>) safe).get("password"));
        assertEquals("[REDACTED]", ((Map<?, ?>) safe).get("accessToken"));
        assertEquals("qa@example.test", ((Map<?, ?>) safe).get("email"));
        assertEquals("Synthetic Person", ((Map<?, ?>) safe).get("name"));
    }

    @Test
    void revealsOnlyOptedInSyntheticRequestPasswordAndKeepsTokensMasked() {
        Map<String, Object> payload = Map.of(
                "password", "synthetic-password",
                "accessToken", "test-token",
                "email", "qa@example.test"
        );

        Object visibleRequest = EvidenceRedactor.redactRequestPayload(payload, true, true);
        Object maskedResponse = EvidenceRedactor.redact(payload, true);
        Object maskedRequest = EvidenceRedactor.redactRequestPayload(payload, true, false);

        assertEquals("synthetic-password", ((Map<?, ?>) visibleRequest).get("password"));
        assertEquals("[REDACTED]", ((Map<?, ?>) visibleRequest).get("accessToken"));
        assertEquals("[REDACTED]", ((Map<?, ?>) maskedResponse).get("password"));
        assertEquals("[REDACTED]", ((Map<?, ?>) maskedRequest).get("password"));
        assertFalse(new EvidenceLogModifier().request(
                "https://qa.example.test/users",
                "{\"password\":\"synthetic-password\"}"
        ).contains("synthetic-password"));
    }

    @Test
    void masksSecretQueryParametersAndSerializedJson() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature123";
        String safeUrl = EvidenceRedactor.redactUrl("https://example.test/users/" + jwt + "?access_token=secret&id=7");
        String safeBody = EvidenceRedactor.redactSerializedBody(
                "{\"password\":\"secret\",\"email\":\"person@example.com\",\"count\":2}");

        assertFalse(safeUrl.contains("secret"));
        assertFalse(safeUrl.contains(jwt));
        assertFalse(safeBody.contains("secret"));
        assertFalse(safeBody.contains("person@example.com"));
        assertEquals(true, safeBody.contains("\"count\":2"));
    }
}
