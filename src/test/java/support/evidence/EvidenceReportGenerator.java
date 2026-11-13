package support.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EvidenceReportGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private EvidenceReportGenerator() {
    }

    public static void generate() {
        List<Map<String, Object>> scenarios = EvidenceCollector.snapshot();
        OffsetDateTime started = parseInstant(System.getProperty("qa.execution.startedAt"));
        OffsetDateTime ended = OffsetDateTime.now();
        String demandId = requiredValue("QA_DEMAND_ID", "Identificador da demanda");
        String analyst = requiredValue("QA_ANALYST_NAME", "Nome do analista responsável");
        String environment = System.getProperty("karate.env",
                System.getenv().getOrDefault("KARATE_ENV", "dev"));
        String apiUrl = System.getProperty("api.base.url");
        if (apiUrl == null || apiUrl.isBlank()) {
            apiUrl = System.getenv("API_BASE_URL");
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            apiUrl = "dev".equals(environment)
                    ? "https://projetinho-nu-two.vercel.app"
                    : "Não informado";
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("project", "QA API Specialist");
        metadata.put("language", "pt-BR");
        metadata.put("documentTitle", "RELATÓRIO DE EVIDÊNCIAS DE TESTES DE API");
        metadata.put("demandId", demandId);
        metadata.put("executionId", "EXEC-" + UUID.randomUUID());
        metadata.put("documentVersion", "1.0");
        metadata.put("projectVersion", System.getProperty("project.version", "não identificado"));
        metadata.put("environment", localizedEnvironment(environment));
        metadata.put("apiUrl", EvidenceRedactor.redactUrl(apiUrl));
        metadata.put("analyst", analyst);
        metadata.put("framework", "Karate DSL");
        metadata.put("startedAt", started.toString());
        metadata.put("endedAt", ended.toString());
        metadata.put("timezone", ZoneId.systemDefault().toString());
        metadata.put("objective", safeContext("QA_OBJECTIVE", "Não informado"));
        metadata.put("origin", safeContext("QA_DEMAND_ORIGIN", "Não informado"));
        metadata.put("functionalDescription", safeContext("QA_FUNCTIONAL_DESCRIPTION", "Não informado"));
        metadata.put("businessRules", safeContext("QA_BUSINESS_RULES", "Não informado"));
        metadata.put("acceptanceCriteria", safeContext("QA_ACCEPTANCE_CRITERIA", "Não informado"));
        metadata.put("dependencies", safeContext("QA_DEPENDENCIES", "Não informado"));
        metadata.put("scope", safeContext("QA_TEST_SCOPE", "Cenários Karate efetivamente carregados nesta execução"));
        metadata.put("outOfScope", safeContext("QA_OUT_OF_SCOPE", "Não informado"));

        Map<String, Object> summary = summarize(scenarios);
        String opinion = opinion(summary);
        List<Map<String, Object>> issues = issues(scenarios);
        Path runDirectory = createRunDirectory(demandId, ended, String.valueOf(metadata.get("executionId")));
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("metadata", metadata);
        document.put("summary", summary);
        document.put("opinion", opinion);
        document.put("issues", issues);
        document.put("scenarios", scenarios);

        try {
            Files.createDirectories(runDirectory.getParent());
            Files.createDirectory(runDirectory);
            String html = renderHtml(metadata, summary, opinion, issues, scenarios);
            byte[] json = MAPPER.writeValueAsBytes(document);
            byte[] pdf = renderPdf(html, runDirectory.toUri().toString());
            Files.createDirectories(runDirectory.resolve("screenshots"));
            Files.createDirectories(runDirectory.resolve("attachments"));
            Files.write(runDirectory.resolve("execution-data.json"), json);
            Files.writeString(runDirectory.resolve("evidencia-qa.html"), html);
            Files.write(runDirectory.resolve("evidencia-qa.pdf"), pdf);
            System.out.println("Evidências de QA geradas: " + runDirectory.toAbsolutePath());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate QA evidence under " + runDirectory, exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to render the QA evidence PDF under " + runDirectory, exception);
        }
    }

    private static String requiredValue(String variable, String prompt) {
        String value = System.getenv(variable);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        java.io.Console console = System.console();
        if (console == null) {
            System.err.println(prompt + " não configurado. Informe-o para gerar as evidências.");
            throw new IllegalStateException("Set " + variable + " before running Maven in a non-interactive environment.");
        }
        value = console.readLine("%s: ", prompt);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " must not be blank.");
        }
        return value.trim();
    }

    private static Path createRunDirectory(String demandId, OffsetDateTime ended, String executionId) {
        if (!demandId.matches("[A-Za-z0-9._-]{1,80}") || demandId.contains("..")) {
            throw new IllegalArgumentException("QA_DEMAND_ID must contain only letters, numbers, '.', '_' or '-'.");
        }
        String root = env("QA_REPORTS_DIR", "reports");
        return Path.of(root, demandId, FILE_DATE.format(ended), executionId);
    }

    private static Map<String, Object> summarize(List<Map<String, Object>> scenarios) {
        long passed = count(scenarios, "PASS");
        long failed = count(scenarios, "FAIL");
        long blocked = count(scenarios, "BLOCKED");
        long notExecuted = count(scenarios, "NOT EXECUTED");
        long executed = passed + failed;
        double passRate = executed == 0 ? 0.0 : Math.round(passed * 10000.0 / executed) / 100.0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planned", scenarios.size());
        result.put("executed", executed);
        result.put("passed", passed);
        result.put("failed", failed);
        result.put("blocked", blocked);
        result.put("notExecuted", notExecuted);
        result.put("passRatePercentOfExecuted", passRate);
        return result;
    }

    private static long count(List<Map<String, Object>> scenarios, String status) {
        return scenarios.stream().filter(scenario -> status.equals(scenario.get("status"))).count();
    }

    private static String opinion(Map<String, Object> summary) {
        long passed = ((Number) summary.get("passed")).longValue();
        long failed = ((Number) summary.get("failed")).longValue();
        long executed = ((Number) summary.get("executed")).longValue();
        long planned = ((Number) summary.get("planned")).longValue();
        if (executed == 0) {
            return "INCONCLUSIVO";
        }
        if (failed > 0) {
            return "INCONCLUSIVO";
        }
        return passed == planned ? "APROVADO" : "APROVADO COM RESSALVAS";
    }

    private static String localizedEnvironment(String environment) {
        return switch (environment.toLowerCase(java.util.Locale.ROOT)) {
            case "dev" -> "Produção";
            case "qa" -> "QA / Teste";
            case "staging" -> "Homologação";
            default -> environment;
        };
    }

    private static List<Map<String, Object>> issues(List<Map<String, Object>> scenarios) {
        List<Map<String, Object>> findings = new ArrayList<>();
        for (Map<String, Object> scenario : scenarios) {
            if (!"FAIL".equals(scenario.get("status"))) {
                continue;
            }
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("id", "PENDING-" + scenario.get("id"));
            issue.put("scenarioId", scenario.get("id"));
            issue.put("description", "Falha de teste requer triagem; não classificada automaticamente como bug.");
            issue.put("endpoint", ((Map<?, ?>) scenario.get("request")).get("url"));
            issue.put("expected", expectedFromGherkin(String.valueOf(scenario.get("gherkin"))));
            issue.put("obtained", ((Map<?, ?>) scenario.get("response")).get("status"));
            issue.put("evidence", scenario.get("error"));
            issue.put("severity", "Não avaliada");
            issue.put("impact", "Avaliação funcional pendente");
            findings.add(issue);
        }
        return findings;
    }

    static String renderHtml(Map<String, Object> metadata, Map<String, Object> summary,
                             String opinion, List<Map<String, Object>> issues,
                             List<Map<String, Object>> scenarios) {
        StringBuilder html = new StringBuilder(16000);
        html.append("""
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" lang="pt-BR">
                <head><meta charset="UTF-8" /><title>Relatório de evidências QA</title>
                <style>
                @page { size: A4; margin: 18mm 15mm 20mm; @bottom-right { content: "Página " counter(page) " de " counter(pages); color: #64748b; font-size: 8pt; } }
                body { font-family: Arial, sans-serif; color: #172033; font-size: 10pt; line-height: 1.45; }
                header { border-bottom: 3px solid #155eef; padding-bottom: 12px; margin-bottom: 18px; }
                h1 { font-size: 19pt; color: #102a56; margin: 0 0 8px; }
                h2 { font-size: 14pt; color: #123b70; margin: 22px 0 8px; border-bottom: 1px solid #cbd5e1; padding-bottom: 4px; }
                h3 { font-size: 11pt; margin: 14px 0 5px; }
                h4 { font-size: 10pt; color: #123b70; margin: 10px 0 5px; }
                .meta, table { width: 100%; border-collapse: collapse; }
                .meta td, th, td { border: 1px solid #d5deea; padding: 6px; vertical-align: top; }
                th { background: #eaf1fb; text-align: left; }
                .status { display: inline-block; font-weight: bold; padding: 3px 7px; border-radius: 3px; }
                .PASS { color: #116329; background: #e6f4ea; } .FAIL { color: #a61b1b; background: #fde8e8; }
                .BLOCKED { color: #8a4b08; background: #fff1d6; } .NOT_EXECUTED { color: #475569; background: #edf1f5; }
                .scenario { border: 1px solid #ccd6e3; border-radius: 4px; padding: 10px; margin: 12px 0; page-break-inside: auto; }
                .code { font-family: Consolas, monospace; font-size: 8pt; white-space: pre-wrap; overflow-wrap: anywhere; background: #f3f6fa; padding: 8px; border: 1px solid #dce3ec; }
                .muted { color: #64748b; } .no-break { page-break-inside: avoid; } .cover { padding-top: 20px; }
                a { color: #155eef; }
                </style></head><body>
                """);
        html.append("<header class=\"cover\"><div class=\"muted\">").append(e(metadata.get("project"))).append("</div><h1>")
                .append(e(metadata.get("documentTitle"))).append("</h1><table class=\"meta\">");
        addMetadataRows(html, metadata);
        html.append("</table></header>");

        html.append("<h2>Sumário</h2><ol>");
        for (String[] section : List.of(
                new String[]{"s1", "Objetivo"}, new String[]{"s2", "Contexto"},
                new String[]{"s3", "Escopo dos testes"}, new String[]{"s4", "Cenários de teste"},
                new String[]{"s5", "BDD — Comportamento esperado"}, new String[]{"s6", "Evidências técnicas"},
                new String[]{"s7", "Resultado consolidado"}, new String[]{"s8", "Bugs e não conformidades"},
                new String[]{"s9", "Parecer final do QA"}, new String[]{"s10", "Responsável pela execução"})) {
            html.append("<li><a href=\"#").append(section[0]).append("\">").append(e(section[1])).append("</a></li>");
        }
        html.append("</ol>");

        section(html, "s1", "1. OBJETIVO", metadata.get("objective"));
        heading(html, "s2", "2. CONTEXTO");
        html.append("<table><tbody>");
        row(html, "Origem da demanda", metadata.get("origin"));
        row(html, "Descrição funcional", metadata.get("functionalDescription"));
        row(html, "Regras de negócio", metadata.get("businessRules"));
        row(html, "Funcionalidade analisada", featureNames(scenarios));
        row(html, "Endpoints relacionados", endpoints(scenarios));
        row(html, "Critérios de aceite", metadata.get("acceptanceCriteria"));
        row(html, "Dependências", metadata.get("dependencies"));
        html.append("</tbody></table>");

        heading(html, "s3", "3. ESCOPO DOS TESTES");
        html.append("<table><tbody>");
        row(html, "Incluído", metadata.get("scope"));
        row(html, "Fora do escopo", metadata.get("outOfScope"));
        html.append("</tbody></table>");
        heading(html, "s4", "4. CENÁRIOS DE TESTE");
        if (scenarios.isEmpty()) {
            html.append("<p>Nenhum cenário foi capturado nesta execução.</p>");
        } else {
            for (Map<String, Object> scenario : scenarios) {
                renderScenario(html, scenario);
            }
        }
        heading(html, "s5", "5. BDD — COMPORTAMENTO ESPERADO");
        for (Map<String, Object> scenario : scenarios) {
            html.append("<h3>").append(e(scenario.get("id"))).append(" — ").append(e(scenario.get("name"))).append("</h3><pre class=\"code\">")
                    .append(e(scenario.get("gherkin"))).append("</pre>");
        }
        heading(html, "s6", "6. EVIDÊNCIAS TÉCNICAS DAS REQUISIÇÕES");
        if (scenarios.isEmpty()) {
            html.append("<p>Não há requisições capturadas.</p>");
        } else {
            for (Map<String, Object> scenario : scenarios) {
                renderTechnicalEvidence(html, scenario);
            }
        }
        heading(html, "s7", "7. RESULTADO CONSOLIDADO");
        html.append("<table><thead><tr><th>Planejados</th><th>Executados</th><th>Aprovados</th><th>Reprovados</th><th>Bloqueados</th><th>Não executados</th><th>Aprovação dos executados</th></tr></thead><tbody><tr>");
        for (String key : List.of("planned", "executed", "passed", "failed", "blocked", "notExecuted", "passRatePercentOfExecuted")) {
            html.append("<td>").append(e(summary.get(key))).append(key.equals("passRatePercentOfExecuted") ? "%" : "").append("</td>");
        }
        html.append("</tr></tbody></table><p class=\"muted\">Cenários planejados correspondem aos cenários observados pelo hook do Karate nesta execução. Percentual considera apenas os executados.</p>");
        heading(html, "s8", "8. BUGS E NÃO CONFORMIDADES");
        if (issues.isEmpty()) {
            html.append("<p>Nenhum bug foi classificado automaticamente.</p>");
        } else {
            html.append("<p>Falhas aguardam triagem; uma falha de teste não foi automaticamente classificada como bug.</p>");
            html.append("<table><thead><tr><th>ID</th><th>Cenário</th><th>Descrição</th><th>Endpoint</th><th>Esperado</th><th>Obtido</th><th>Evidência técnica</th><th>Severidade</th><th>Impacto</th></tr></thead><tbody>");
            for (Map<String, Object> issue : issues) {
                html.append("<tr>");
                for (String key : List.of("id", "scenarioId", "description", "endpoint", "expected", "obtained", "evidence", "severity", "impact")) {
                    html.append("<td>").append(e(issue.get(key))).append("</td>");
                }
                html.append("</tr>");
            }
            html.append("</tbody></table>");
        }
        heading(html, "s9", "9. PARECER FINAL DO QA");
        html.append("<p><strong>").append(e(opinion)).append("</strong></p>");
        heading(html, "s10", "10. RESPONSÁVEL PELA EXECUÇÃO");
        html.append("<table><tbody>");
        row(html, "Analista", metadata.get("analyst"));
        row(html, "Data", displayDate(metadata.get("endedAt")));
        row(html, "Hora / fuso", displayTime(metadata.get("endedAt")) + " — " + metadata.get("timezone"));
        row(html, "Framework", metadata.get("framework"));
        row(html, "Execução", metadata.get("executionId"));
        html.append("</tbody></table></body></html>");
        return html.toString();
    }

    static byte[] renderPdf(String html, String baseUri) throws Exception {
        try (var output = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, baseUri)
                    .toStream(output)
                    .run();
            return output.toByteArray();
        }
    }

    private static void addMetadataRows(StringBuilder html, Map<String, Object> metadata) {
        html.append("<tbody>");
        for (String[] row : List.of(
                new String[]{"Demanda", "demandId"}, new String[]{"Execução", "executionId"},
                new String[]{"Versão do documento", "documentVersion"}, new String[]{"Versão do projeto", "projectVersion"},
                new String[]{"Ambiente", "environment"}, new String[]{"API", "apiUrl"},
                new String[]{"Analista responsável", "analyst"}, new String[]{"Framework", "framework"},
                new String[]{"Data", "endedAt"}, new String[]{"Início", "startedAt"},
                new String[]{"Término", "endedAt"}, new String[]{"Fuso horário", "timezone"})) {
            html.append("<tr><th>").append(e(row[0])).append("</th><td>").append(e(formattedMetadata(row[1], metadata.get(row[1])))).append("</td></tr>");
        }
        html.append("</tbody>");
    }

    private static String formattedMetadata(String key, Object value) {
        return "startedAt".equals(key) || "endedAt".equals(key)
                ? displayDate(value) + " " + displayTime(value) : String.valueOf(value);
    }

    private static void renderScenario(StringBuilder html, Map<String, Object> scenario) {
        String status = String.valueOf(scenario.get("status"));
        String statusLabel = String.valueOf(scenario.getOrDefault("statusLabel", localizedStatus(status)));
        html.append("<div class=\"scenario\"><h3>").append(e(scenario.get("id"))).append(" — ").append(e(scenario.get("name")))
                .append(" <span class=\"status ").append(status.replace(' ', '_')).append("\">").append(e(statusLabel)).append("</span></h3><table><tbody>");
        row(html, "Descrição", scenario.get("description"));
        row(html, "BDD executado", scenario.get("gherkin"));
        row(html, "Prioridade", scenario.get("priority"));
        row(html, "Tipo", scenario.get("category"));
        row(html, "Pré-condições", preconditions(scenario.get("gherkin")));
        row(html, "Dados utilizados", ((Map<?, ?>) scenario.get("request")).get("payload"));
        row(html, "Resultado esperado", expectedFromGherkin(String.valueOf(scenario.get("gherkin"))));
        row(html, "Resultado obtido", ((Map<?, ?>) scenario.get("response")).get("status"));
        row(html, "Arquivo .feature", scenario.get("featureFile"));
        row(html, "Status", statusLabel);
        if (!String.valueOf(scenario.get("blockedReason")).isBlank()) {
            row(html, "Motivo do bloqueio", scenario.get("blockedReason"));
        }
        html.append("</tbody></table></div>");
    }

    private static void renderTechnicalEvidence(StringBuilder html, Map<String, Object> scenario) {
        Map<?, ?> request = (Map<?, ?>) scenario.get("request");
        Map<?, ?> response = (Map<?, ?>) scenario.get("response");
        html.append("<div class=\"scenario\"><h3>").append(e(scenario.get("id"))).append(" — ")
                .append(e(scenario.get("name"))).append("</h3><h4>Requisição</h4><table><tbody>");
        row(html, "Endpoint", request.get("url"));
        row(html, "Método HTTP", request.get("method"));
        row(html, "URL completa (credenciais mascaradas)", request.get("url"));
        row(html, "Executada em", displayDate(scenario.get("startedAt")) + " " + displayTime(scenario.get("startedAt"))
                + " (" + e(scenario.get("environment")) + ")");
        row(html, "Headers da requisição", pretty(request.get("headers")));
        row(html, "Content-Type", request.get("contentType"));
        row(html, "Correlação", request.get("correlationId"));
        row(html, "Payload enviado", pretty(request.get("payload")));
        html.append("</tbody></table><h4>Resposta da API</h4><table><tbody>");
        row(html, "Status HTTP", response.get("status"));
        row(html, "Headers da resposta", pretty(response.get("headers")));
        row(html, "Body da resposta", pretty(response.get("body")));
        row(html, "Tempo de resposta (ms)", response.get("durationMs"));
        row(html, "Recebida em", displayDate(scenario.get("receivedAt")) + " " + displayTime(scenario.get("receivedAt")));
        html.append("</tbody></table><h4>Validação do cenário</h4><table><tbody>");
        row(html, "Validações / esperado", expectedFromGherkin(String.valueOf(scenario.get("gherkin"))));
        row(html, "Resultado da validação", scenario.getOrDefault("statusLabel",
                localizedStatus(scenario.get("status"))));
        row(html, "Observação", "Corpo e mensagens da resposta são apresentados conforme recebidos da API.");
        html.append("</tbody></table><p class=\"muted\">Arquivo: ").append(e(scenario.get("featureFile"))).append("</p></div>");
    }

    private static void section(StringBuilder html, String id, String title, Object value) {
        heading(html, id, title);
        html.append("<p>").append(e(value)).append("</p>");
    }

    private static void heading(StringBuilder html, String id, String title) {
        html.append("<h2 id=\"").append(e(id)).append("\">").append(e(title)).append("</h2>");
    }

    private static void row(StringBuilder html, String label, Object value) {
        html.append("<tr><th>").append(e(label)).append("</th><td>");
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            html.append("<pre class=\"code\">").append(e(pretty(value))).append("</pre>");
        } else {
            html.append(e(value));
        }
        html.append("</td></tr>");
    }

    private static String preconditions(Object rawGherkin) {
        String gherkin = String.valueOf(rawGherkin);
        return gherkin.lines().filter(line -> line.trim().matches("(?i)^(Given|And|But|Dado|Dada|Dados|Dadas|E|Mas)\\b.*"))
                .reduce((left, right) -> left + "\n" + right).orElse("Não informado");
    }

    private static String expectedFromGherkin(String gherkin) {
        return gherkin.lines().filter(line -> line.trim().matches("(?i)^(Then|Então|Entao)\\b.*"))
                .reduce((left, right) -> left + "\n" + right).orElse("Não há passo de validação capturado");
    }

    private static String localizedStatus(Object status) {
        return switch (String.valueOf(status)) {
            case "PASS" -> "Aprovado";
            case "FAIL" -> "Reprovado";
            case "BLOCKED" -> "Bloqueado";
            case "NOT EXECUTED" -> "Não executado";
            default -> String.valueOf(status);
        };
    }

    private static String featureNames(List<Map<String, Object>> scenarios) {
        return scenarios.stream().map(scenario -> String.valueOf(scenario.get("feature")))
                .filter(name -> !name.isBlank()).distinct().reduce((a, b) -> a + ", " + b)
                .orElse("Não informado");
    }

    private static String endpoints(List<Map<String, Object>> scenarios) {
        return scenarios.stream().map(scenario -> String.valueOf(((Map<?, ?>) scenario.get("request")).get("url")))
                .filter(url -> !url.isBlank()).distinct().reduce((a, b) -> a + ", " + b)
                .orElse("Não capturados");
    }

    private static String pretty(Object value) {
        if (value == null) {
            return "Não capturado";
        }
        try {
            if (value instanceof String text) {
                return text;
            }
            return MAPPER.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to format captured evidence", exception);
        }
    }

    private static String e(Object value) {
        if (value == null) {
            return "Não informado";
        }
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static OffsetDateTime parseInstant(String value) {
        return value == null || value.isBlank()
                ? OffsetDateTime.now()
                : Instant.parse(value).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static String displayDate(Object value) {
        return formatTime(value, DISPLAY_DATE);
    }

    private static String displayTime(Object value) {
        return formatTime(value, DISPLAY_TIME);
    }

    private static String formatTime(Object value, DateTimeFormatter formatter) {
        if (value == null) {
            return "Não informado";
        }
        try {
            return formatter.format(Instant.parse(String.valueOf(value)).atZone(ZoneId.systemDefault()));
        } catch (RuntimeException exception) {
            return "Não informado";
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String safeContext(String name, String fallback) {
        return EvidenceRedactor.redactTextContent(env(name, fallback));
    }
}
