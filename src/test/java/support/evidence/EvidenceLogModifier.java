package support.evidence;

import com.intuit.karate.http.HttpLogModifier;

public final class EvidenceLogModifier implements HttpLogModifier {

    @Override
    public boolean enableForUri(String uri) {
        return true;
    }

    @Override
    public String uri(String uri) {
        return EvidenceRedactor.redactUrl(uri, false);
    }

    @Override
    public String header(String header, String value) {
        return EvidenceRedactor.isSensitiveField(header, false) ? "[REDACTED]" : value;
    }

    @Override
    public String request(String uri, String request) {
        return EvidenceRedactor.redactSerializedBody(request, false);
    }

    @Override
    public String response(String uri, String response) {
        return EvidenceRedactor.redactSerializedBody(response, false);
    }
}
