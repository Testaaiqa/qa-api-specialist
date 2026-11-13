package support.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

final class EvidenceRedactor {

    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie",
            "x-api-key", "api-key", "apikey"
    );
    private static final Pattern SECRET_TEXT = Pattern.compile(
            "(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*|"
                    + "(\"?(?:password|passwd|secret|client[_-]?secret|access[_-]?token|refresh[_-]?token|api[_-]?key)\"?\\s*[:=]\\s*\"?)[^\",\\s}]+"
    );
    private static final Pattern JWT_TEXT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"
    );
    private static final Pattern EMAIL_TEXT = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"
    );
    private static final Pattern PHONE_TEXT = Pattern.compile(
            "(?<!\\d)\\+?\\d[\\d\\s().-]{7,}\\d(?!\\d)"
    );
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> EXTRA_SENSITIVE_FIELDS = configuredSensitiveFields();

    private EvidenceRedactor() {
    }

    static Object redact(Object value) {
        return redact(value, null, revealSyntheticData(), false);
    }

    static Object redact(Object value, boolean revealSyntheticData) {
        return redact(value, null, revealSyntheticData, false);
    }

    static Object redactRequestPayload(Object value) {
        boolean revealData = revealSyntheticData();
        boolean revealPassword = revealData
                && "true".equalsIgnoreCase(System.getenv("QA_REVEAL_SYNTHETIC_PASSWORDS"));
        return redact(value, null, revealData, revealPassword);
    }

    static Object redactRequestPayload(Object value, boolean revealSyntheticData, boolean revealPassword) {
        return redact(value, null, revealSyntheticData, revealSyntheticData && revealPassword);
    }

    private static Object redact(Object value, String key, boolean revealSyntheticData, boolean revealPassword) {
        if (key != null && isSensitiveField(key, revealSyntheticData, revealPassword)) {
            return REDACTED;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            map.forEach((mapKey, mapValue) -> {
                String name = String.valueOf(mapKey);
                redacted.put(name, redact(mapValue, name, revealSyntheticData, revealPassword));
            });
            return redacted;
        }
        if (value instanceof Iterable<?> values) {
            List<Object> redacted = new ArrayList<>();
            values.forEach(item -> redacted.add(redact(item, key, revealSyntheticData, revealPassword)));
            return redacted;
        }
        if (value instanceof String text) {
            return redactSerializedBody(text, revealSyntheticData, revealPassword);
        }
        return value;
    }

    static boolean isSensitiveField(String name) {
        return isSensitiveField(name, revealSyntheticData());
    }

    static boolean isSensitiveField(String name, boolean revealSyntheticData) {
        return isSensitiveField(name, revealSyntheticData, false);
    }

    private static boolean isSensitiveField(String name, boolean revealSyntheticData, boolean revealPassword) {
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        boolean password = normalized.contains("password") || normalized.contains("passwd");
        boolean canRevealPassword = revealPassword && normalized.equals("password");
        boolean credential = (password && !canRevealPassword)
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("apikey")
                || normalized.contains("authorization")
                || normalized.contains("cookie");
        boolean personal = normalized.contains("username")
                || normalized.equals("email")
                || normalized.contains("phone")
                || normalized.contains("mobile")
                || normalized.equals("name")
                || normalized.contains("firstname")
                || normalized.contains("lastname")
                || normalized.contains("fullname")
                || normalized.equals("cpf")
                || normalized.equals("cnpj")
                || normalized.equals("rg")
                || normalized.contains("taxid")
                || normalized.contains("personalid")
                || normalized.equals("ssn")
                || normalized.contains("document")
                || normalized.contains("birth")
                || normalized.contains("address");
        return EXTRA_SENSITIVE_FIELDS.contains(normalized)
                || credential
                || (!revealSyntheticData && personal);
    }

    static Object redactHeaders(Object headers) {
        if (!(headers instanceof Map<?, ?> map)) {
            return redact(headers);
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            String header = String.valueOf(key);
            safe.put(header, SENSITIVE_HEADERS.contains(header.toLowerCase(Locale.ROOT))
                    ? REDACTED : redact(value));
        });
        return safe;
    }

    static String redactUrl(String rawUrl) {
        return redactUrl(rawUrl, revealSyntheticData());
    }

    static String redactUrl(String rawUrl, boolean revealSyntheticData) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return rawUrl;
        }
        try {
            URI uri = new URI(rawUrl);
            String authority = uri.getRawAuthority();
            if (uri.getRawUserInfo() != null) {
                authority = REDACTED + "@" + authority.substring(authority.lastIndexOf('@') + 1);
            }
            String query = uri.getRawQuery();
            if (query != null) {
                String[] parameters = query.split("&");
                for (int i = 0; i < parameters.length; i++) {
                    int separator = parameters[i].indexOf('=');
                    String name = separator < 0 ? parameters[i] : parameters[i].substring(0, separator);
                    if (isSensitiveField(name, revealSyntheticData)) {
                        parameters[i] = name + "=" + REDACTED;
                    }
                }
                query = String.join("&", parameters);
            }
            StringBuilder safe = new StringBuilder();
            if (uri.getScheme() != null) {
                safe.append(uri.getScheme()).append(':');
            }
            if (authority != null) {
                safe.append("//").append(authority);
            }
            if (uri.getRawPath() != null) {
                safe.append(uri.getRawPath());
            }
            if (query != null) {
                safe.append('?').append(query);
            }
            if (uri.getRawFragment() != null) {
                safe.append('#').append(uri.getRawFragment());
            }
            return JWT_TEXT.matcher(safe).replaceAll(REDACTED);
        } catch (URISyntaxException exception) {
            return redactFreeText(rawUrl, revealSyntheticData);
        }
    }

    static String redactSerializedBody(String body) {
        return redactSerializedBody(body, revealSyntheticData());
    }

    static String redactSerializedBody(String body, boolean revealSyntheticData) {
        return redactSerializedBody(body, revealSyntheticData, false);
    }

    private static String redactSerializedBody(String body, boolean revealSyntheticData, boolean revealPassword) {
        if (body == null || body.isBlank()) {
            return body;
        }
        try {
            Object parsed = MAPPER.readValue(body, Object.class);
            return MAPPER.writeValueAsString(redact(parsed, null, revealSyntheticData, revealPassword));
        } catch (IOException notJson) {
            return redactFreeText(body, revealSyntheticData);
        }
    }

    static String redactTextContent(String text) {
        return redactFreeText(text, revealSyntheticData());
    }

    private static String redactText(String text) {
        Matcher matcher = SECRET_TEXT.matcher(text);
        StringBuffer safe = new StringBuffer();
        while (matcher.find()) {
            String prefix = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(safe, Matcher.quoteReplacement(prefix + REDACTED));
        }
        matcher.appendTail(safe);
        return JWT_TEXT.matcher(safe).replaceAll(REDACTED);
    }

    private static String redactFreeText(String text, boolean revealSyntheticData) {
        String safe = redactText(text);
        return revealSyntheticData ? safe
                : PHONE_TEXT.matcher(EMAIL_TEXT.matcher(safe).replaceAll(REDACTED)).replaceAll(REDACTED);
    }

    private static boolean revealSyntheticData() {
        return "true".equalsIgnoreCase(System.getenv("API_TEST_DATA_SYNTHETIC"))
                && "true".equalsIgnoreCase(System.getenv("QA_REVEAL_SYNTHETIC_DATA"));
    }

    private static Set<String> configuredSensitiveFields() {
        String configured = System.getenv("QA_SENSITIVE_FIELDS");
        if (configured == null || configured.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(field -> !field.isEmpty())
                .map(field -> field.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
