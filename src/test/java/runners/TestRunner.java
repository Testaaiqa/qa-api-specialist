package runners;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import support.evidence.EvidenceCollector;
import support.evidence.EvidenceReportGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestRunner {

    @BeforeAll
    static void startEvidenceCollection() {
        System.setProperty("qa.execution.startedAt", java.time.Instant.now().toString());
        EvidenceCollector.reset();
    }

    @Test
    void runApiFeatures() {
        String featurePath = System.getProperty("qa.features.path", "classpath:features");
        Results results = Runner.path(featurePath)
                .failWhenNoScenariosFound(true)
                .outputHtmlReport(false)
                .parallel(1);
        assertEquals(0, results.getFailCount(),
                "One or more Karate scenarios failed; inspect the sanitized QA evidence.");
    }

    @AfterAll
    static void generateEvidence() {
        EvidenceReportGenerator.generate();
    }
}
