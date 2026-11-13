package support.evidence;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceHookIntegrationTest {

    @Test
    void afterScenarioAutomaticallyCapturesTheHttpExchange() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/evidence", exchange -> {
            byte[] body = "{\"message\":\"captured\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        EvidenceCollector.reset();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            Results results = Runner.path("classpath:support/evidence/evidence-hook-test.feature")
                    .karateEnv("qa")
                    .systemProperty("api.base.url", baseUrl)
                    .outputHtmlReport(false)
                    .parallel(1);

            assertEquals(0, results.getFailCount(), "Karate evidence hook integration scenario must pass.");
            List<Map<String, Object>> captured = EvidenceCollector.snapshot();
            assertEquals(1, captured.size());
            Map<String, Object> scenario = captured.get(0);
            assertEquals("EVIDENCE-HOOK-TEST", scenario.get("id"),
                    "Captured tags: " + scenario.get("tags"));
            assertEquals("PASS", scenario.get("status"));
            assertEquals("GET", ((Map<?, ?>) scenario.get("request")).get("method"));
            assertTrue(String.valueOf(((Map<?, ?>) scenario.get("request")).get("url")).endsWith("/evidence"));
            assertEquals(200, ((Map<?, ?>) scenario.get("response")).get("status"));
            assertEquals("captured", ((Map<?, ?>) ((Map<?, ?>) scenario.get("response"))
                    .get("body")).get("message"));
        } finally {
            server.stop(0);
            EvidenceCollector.reset();
        }
    }
}
