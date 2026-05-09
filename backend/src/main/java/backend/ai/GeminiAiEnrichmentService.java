package backend.ai;

import backend.ai.dto.AiEnrichmentRequest;
import backend.ai.dto.AiEnrichmentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
@Requires(property = "ai.provider", value = "gemini", defaultValue = "gemini")
public class GeminiAiEnrichmentService implements AiEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiEnrichmentService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${google.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    public GeminiAiEnrichmentService(
        @Client("https://generativelanguage.googleapis.com") HttpClient httpClient,
        ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AiEnrichmentResponse> enrich(AiEnrichmentRequest request) {
        if(apiKey == null || apiKey.isBlank()) {
            log.warn("GOOGLE_API_KEY not configured — skipping AI enrichment");
            return Optional.empty();
        }
        try {
            String prompt = buildPrompt(request);
            Map<String, Object> body = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json"));
            String url = "/v1beta/models/" + model + ":generateContent";
            String responseJson = httpClient.toBlocking().retrieve(
                HttpRequest.POST(url, body)
                    .contentType(MediaType.APPLICATION_JSON_TYPE)
                    .header("x-goog-api-key", apiKey),
                String.class);
            return Optional.of(parseGeminiResponse(responseJson));
        } catch(Exception e) {
            log.error("AI enrichment failed for title='{}': {}", request.title(), e.getMessage());
            return Optional.empty();
        }
    }

    private String buildPrompt(AiEnrichmentRequest req) {
        try {
            String inputJson = objectMapper.writeValueAsString(Map.of(
                "title", nullToEmpty(req.title()),
                "abstract", nullToEmpty(req.abstractText()),
                "intendedAudience", nullToEmpty(req.intendedAudience()),
                "suggestedKeywords", nullToEmpty(req.suggestedKeywords()),
                "speakerBios", req.speakerBios() != null ? req.speakerBios() : List.of()));
            return """
                You are a multilingual technical content specialist for a software conference platform.
                
                You will receive metadata about a tech conference talk as JSON.
                Your tasks:
                1. Translate/rewrite ALL text fields into both English and Norwegian, regardless of their original language.
                2. Suggest 5-8 ADDITIONAL search keywords (beyond the suggestedKeywords already provided) in each language that would help people find this talk.
                   The aiKeywords should be different from suggestedKeywords — they are supplementary.
                
                Input:
                %s
                
                Respond ONLY with a valid JSON object matching this exact schema:
                {
                  "english": {
                    "title": "...",
                    "abstract": "...",
                    "intendedAudience": "...",
                    "aiKeywords": ["...", ...]
                  },
                  "norwegian": {
                    "title": "...",
                    "abstract": "...",
                    "intendedAudience": "...",
                    "aiKeywords": ["...", ...]
                  }
                }
                """
                .formatted(inputJson);
        } catch(Exception e) {
            throw new RuntimeException("Failed to build AI prompt", e);
        }
    }

    private AiEnrichmentResponse parseGeminiResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        String text = root
            .path("candidates").get(0)
            .path("content").path("parts").get(0)
            .path("text").asText();
        return objectMapper.readValue(text, AiEnrichmentResponse.class);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
