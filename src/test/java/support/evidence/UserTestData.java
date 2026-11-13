package support.evidence;

import java.util.UUID;

public final class UserTestData {

    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "");

    private UserTestData() {
    }

    public static String validEmail() {
        String domain = System.getenv("QA_TEST_EMAIL_DOMAIN");
        if (domain == null || domain.isBlank()) {
            throw new IllegalStateException("Set QA_TEST_EMAIL_DOMAIN to an approved test domain.");
        }
        domain = domain.trim();
        if (!domain.matches("(?i)[a-z0-9.-]+\\.[a-z]{2,}")) {
            throw new IllegalArgumentException("QA_TEST_EMAIL_DOMAIN must be a valid DNS domain.");
        }
        return "qa-" + RUN_ID.substring(0, 12) + "@" + domain;
    }

    public static String malformedEmail() {
        return "qa-invalid-" + RUN_ID;
    }

    public static String password() {
        return "QaTest-" + RUN_ID;
    }
}
