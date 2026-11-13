package support.evidence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class EvidenceCollector {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ConcurrentLinkedQueue<Map<String, Object>> SCENARIOS = new ConcurrentLinkedQueue<>();

    private EvidenceCollector() {
    }

    public static void reset() {
        SCENARIOS.clear();
    }

    public static void capture(String json) {
        try {
            Map<String, Object> raw = MAPPER.readValue(json, new TypeReference<>() {
            });
            SCENARIOS.add(normalize(raw));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to capture Karate scenario evidence", exception);
        }
    }

    public static List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> copy = new ArrayList<>(SCENARIOS);
        copy.sort(Comparator.comparing(scenario -> String.valueOf(scenario.get("startedAt"))));
        return copy;
    }

    private static Map<String, Object> normalize(Map<String, Object> raw) throws IOException {
        Map<String, Object> scenario = new LinkedHashMap<>();
        String capturedAt = Instant.now().toString();
        Map<String, Object> request = asMap(raw.get("request"));
        Map<String, Object> response = asMap(raw.get("response"));

        scenario.put("id", scenarioId(raw));
        scenario.put("name", EvidenceRedactor.redactTextContent(text(raw.get("scenarioName"), "Cenário sem nome")));
        scenario.put("description", EvidenceRedactor.redactTextContent(text(raw.get("scenarioDescription"), "")));
        String sourceFile = text(raw.get("featureFile"), "");
        scenario.put("featureFile", displayFeatureFile(sourceFile));
        scenario.put("tags", EvidenceRedactor.redact(safeList(raw.get("tags"))));
        scenario.put("environment", localizedEnvironment(text(raw.get("environment"), "desconhecido")));
        scenario.put("startedAt", startedAt(capturedAt, response.get("durationMs"), response.get("requestTimestamp")));
        scenario.put("receivedAt", capturedAt);
        String gherkin = readGherkin(sourceFile, text(raw.get("scenarioName"), ""));
        scenario.put("gherkin", EvidenceRedactor.redactTextContent(gherkin));
        scenario.put("feature", EvidenceRedactor.redactTextContent(featureTitle(gherkin)));
        scenario.put("priority", valueForTag(scenario.get("tags"), "priority", "Não especificada"));
        scenario.put("category", valueForTag(scenario.get("tags"), "type", "API"));

        Object status = response.get("status");
        String error = text(raw.get("errorMessage"), "");
        String blockedReason = text(raw.get("blockedReason"), "");
        if (status == null && blockedReason.isBlank() && !error.isBlank()) {
            blockedReason = "Nenhuma resposta HTTP foi recebida; execução interrompida antes da validação.";
        }
        String result = status == null
                ? (blockedReason.isBlank() ? "NOT EXECUTED" : "BLOCKED")
                : (error.isBlank() ? "PASS" : "FAIL");
        scenario.put("status", result);
        scenario.put("statusLabel", statusLabel(result));
        scenario.put("blockedReason", blockedReason);
        scenario.put("error", error.isBlank() ? "" : "Validação Karate falhou; detalhes sensíveis foram omitidos.");

        Map<String, Object> safeRequest = new LinkedHashMap<>();
        safeRequest.put("method", text(request.get("method"), "Não capturado"));
        safeRequest.put("url", EvidenceRedactor.redactUrl(text(request.get("url"), "")));
        safeRequest.put("headers", EvidenceRedactor.redactHeaders(request.get("headers")));
        safeRequest.put("contentType", text(request.get("contentType"), header(request.get("headers"), "content-type")));
        safeRequest.put("payload", EvidenceRedactor.redactRequestPayload(decodePayload(request.get("payload"))));
        safeRequest.put("correlationId", text(request.get("correlationId"), header(request.get("headers"), "x-correlation-id")));
        scenario.put("request", safeRequest);

        Map<String, Object> safeResponse = new LinkedHashMap<>();
        safeResponse.put("status", status);
        safeResponse.put("headers", EvidenceRedactor.redactHeaders(response.get("headers")));
        safeResponse.put("body", EvidenceRedactor.redact(response.get("body")));
        safeResponse.put("durationMs", response.get("durationMs"));
        safeResponse.put("requestTimestamp", response.get("requestTimestamp"));
        scenario.put("response", safeResponse);
        return scenario;
    }

    private static Object decodePayload(Object payload) {
        if (!(payload instanceof List<?> bytes) || bytes.isEmpty()) {
            return payload;
        }
        byte[] body = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            Object value = bytes.get(i);
            if (!(value instanceof Number number)) {
                return payload;
            }
            int byteValue = number.intValue();
            if (byteValue < Byte.MIN_VALUE || byteValue > 0xff) {
                return payload;
            }
            body[i] = (byte) byteValue;
        }
        try {
            return MAPPER.readValue(new String(body, java.nio.charset.StandardCharsets.UTF_8), Object.class);
        } catch (IOException notJson) {
            return payload;
        }
    }

    private static String scenarioId(Map<String, Object> raw) {
        for (Object tag : safeList(raw.get("tags"))) {
            String value = tagName(tag);
            if (value.startsWith("@id=") || value.startsWith("id=")) {
                return value.substring(value.indexOf('=') + 1);
            }
        }
        String source = text(raw.get("featureFile"), "") + "|" + text(raw.get("scenarioName"), "");
        return "SCN-" + digest(source).substring(0, 10).toUpperCase(Locale.ROOT);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String startedAt(String receivedAt, Object duration, Object requestTimestamp) {
        if (requestTimestamp instanceof Number timestamp) {
            return Instant.ofEpochMilli(timestamp.longValue()).toString();
        }
        if (requestTimestamp instanceof String timestamp && !timestamp.isBlank()) {
            try {
                return Instant.parse(timestamp).toString();
            } catch (RuntimeException notIsoTimestamp) {
                try {
                    return Instant.ofEpochMilli(Long.parseLong(timestamp)).toString();
                } catch (NumberFormatException notEpochTimestamp) {
                    return receivedAt;
                }
            }
        }
        if (!(duration instanceof Number number)) {
            return receivedAt;
        }
        return Instant.parse(receivedAt).minusMillis(number.longValue()).toString();
    }

    private static String readGherkin(String featureFile, String scenarioName) {
        Path source = resolveFeature(featureFile);
        if (source == null || !Files.isRegularFile(source)) {
            return "";
        }
        try {
            List<String> lines = Files.readAllLines(source);
            StringBuilder bdd = new StringBuilder();
            String featureLine = lines.stream().map(String::trim)
                    .filter(line -> line.matches("(?i)^(Feature|Funcionalidade):.*"))
                    .findFirst().orElse("");
            boolean inScenario = false;
            boolean inExamples = false;
            boolean foundScenario = false;
            Map<String, String> substitutions = Map.of();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.matches("(?i)^(Scenario(?: Outline)?|Cenário):.*")) {
                    if (inScenario) {
                        break;
                    }
                    substitutions = scenarioNameSubstitutions(trimmed, scenarioName);
                    inScenario = substitutions != null;
                    foundScenario = inScenario;
                    if (inScenario) {
                        bdd.append(localizeGherkinLine(featureLine)).append('\n')
                                .append(localizeGherkinLine(
                                        substituteExamples(trimmed, substitutions)
                                                .replaceFirst("(?i)^Scenario Outline:", "Scenario:")
                                )).append('\n');
                    }
                    continue;
                }
                if (inScenario && trimmed.matches("(?i)^Examples:.*")) {
                    inExamples = true;
                    continue;
                }
                if (inScenario && !inExamples
                        && (trimmed.matches("(?i)^(Given|When|Then|And|But|Dado|Dada|Dados|Dadas|Quando|Então|Entao|E|Mas)\\b.*")
                        || trimmed.startsWith("|") || trimmed.startsWith("\"\"\""))) {
                    bdd.append(localizeGherkinLine(substituteExamples(line.stripTrailing(), substitutions))).append('\n');
                }
            }
            return foundScenario ? bdd.toString().stripTrailing() : "";
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read feature file: " + source, exception);
        }
    }

    private static Map<String, String> scenarioNameSubstitutions(String scenarioLine, String scenarioName) {
        String template = scenarioLine.substring(scenarioLine.indexOf(':') + 1).trim();
        java.util.regex.Matcher placeholders = java.util.regex.Pattern.compile("<([^>]+)>").matcher(template);
        if (!placeholders.find()) {
            return template.equals(scenarioName) ? Map.of() : null;
        }

        StringBuilder expression = new StringBuilder("^");
        List<String> names = new ArrayList<>();
        int previousEnd = 0;
        do {
            expression.append(java.util.regex.Pattern.quote(template.substring(previousEnd, placeholders.start())))
                    .append("(.*?)");
            names.add(placeholders.group(1));
            previousEnd = placeholders.end();
        } while (placeholders.find());
        expression.append(java.util.regex.Pattern.quote(template.substring(previousEnd))).append('$');

        java.util.regex.Matcher resolved = java.util.regex.Pattern.compile(expression.toString()).matcher(scenarioName);
        if (!resolved.matches()) {
            return null;
        }
        Map<String, String> substitutions = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            substitutions.put(names.get(i), resolved.group(i + 1));
        }
        return substitutions;
    }

    private static String substituteExamples(String line, Map<String, String> substitutions) {
        String result = line;
        for (Map.Entry<String, String> entry : substitutions.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
    }

    private static String featureTitle(String gherkin) {
        return gherkin.lines().filter(line -> line.trim().matches("(?i)^(Feature|Funcionalidade):.*"))
                .map(line -> line.trim().substring(line.indexOf(':') + 1).trim())
                .findFirst().orElse("");
    }

    private static String localizeGherkinLine(String line) {
        String leadingWhitespace = line.substring(0, line.length() - line.stripLeading().length());
        String content = line.stripLeading();
        if (content.matches("(?i)^Feature:.*")) {
            return leadingWhitespace + content.replaceFirst("(?i)^Feature:", "Funcionalidade:");
        }
        if (content.matches("(?i)^Scenario(?: Outline)?:.*")) {
            return leadingWhitespace + content.replaceFirst("(?i)^Scenario Outline:", "Esquema do cenário:")
                    .replaceFirst("(?i)^Scenario:", "Cenário:");
        }
        java.util.regex.Matcher step = java.util.regex.Pattern
                .compile("(?i)^(Given|When|Then|And|But)\\b").matcher(content);
        if (!step.find()) {
            return line;
        }
        String localizedKeyword = switch (step.group(1).toLowerCase(Locale.ROOT)) {
            case "given" -> "Dado";
            case "when" -> "Quando";
            case "then" -> "Então";
            case "and" -> "E";
            case "but" -> "Mas";
            default -> step.group(1);
        };
        return leadingWhitespace + localizedKeyword + content.substring(step.end());
    }

    private static Path resolveFeature(String featureFile) {
        String normalized = featureFile.replace('\\', '/');
        int classpath = normalized.indexOf("features/");
        if (classpath >= 0) {
            normalized = normalized.substring(classpath);
        } else {
            int source = normalized.indexOf("src/test/java/");
            if (source >= 0) {
                normalized = normalized.substring(source + "src/test/java/".length());
            }
        }
        if ((!normalized.startsWith("features/") && !normalized.startsWith("support/evidence/"))
                || normalized.contains("..")) {
            return null;
        }
        return Path.of("src", "test", "java").resolve(normalized.replace('/', java.io.File.separatorChar));
    }

    private static String displayFeatureFile(String featureFile) {
        String normalized = featureFile.replace('\\', '/');
        int features = normalized.indexOf("features/");
        if (features >= 0) {
            return normalized.substring(features);
        }
        int evidence = normalized.indexOf("support/evidence/");
        if (evidence >= 0) {
            return normalized.substring(evidence);
        }
        return normalized.isBlank() ? "Não capturado" : Path.of(normalized).getFileName().toString();
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "PASS" -> "Aprovado";
            case "FAIL" -> "Reprovado";
            case "BLOCKED" -> "Bloqueado";
            case "NOT EXECUTED" -> "Não executado";
            default -> status;
        };
    }

    private static String localizedEnvironment(String environment) {
        return switch (environment.toLowerCase(Locale.ROOT)) {
            case "dev" -> "Produção";
            case "qa" -> "QA / Teste";
            case "staging" -> "Homologação";
            default -> EvidenceRedactor.redactTextContent(environment);
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<?> safeList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        return List.of();
    }

    private static String valueForTag(Object rawTags, String tagName, String fallback) {
        for (Object tag : safeList(rawTags)) {
            String value = tagName(tag);
            String prefix = "@" + tagName + "=";
            String normalizedPrefix = prefix.substring(1);
            if (value.startsWith(prefix) || value.startsWith(normalizedPrefix)) {
                return value.substring(value.indexOf('=') + 1);
            }
        }
        return fallback;
    }

    private static String tagName(Object tag) {
        String value = tag instanceof Map<?, ?> map
                ? text(map.get("name"), String.valueOf(tag))
                : String.valueOf(tag);
        return value.startsWith("@") ? value.substring(1) : value;
    }

    private static String header(Object headers, String soughtName) {
        if (headers instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (soughtName.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                    Object value = entry.getValue();
                    return value instanceof List<?> list && !list.isEmpty()
                            ? String.valueOf(list.get(0)) : text(value, "");
                }
            }
        }
        return "";
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
}
