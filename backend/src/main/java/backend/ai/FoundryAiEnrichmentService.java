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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
@Requires(property = "ai.provider", value = "foundry")
public class FoundryAiEnrichmentService implements AiEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(FoundryAiEnrichmentService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${foundry.api.key:}")
    private String apiKey;

    @Value("${foundry.api.url:}")
    private String apiUrl;
    
    @Value("${foundry.model:}")
    private String model;

    public FoundryAiEnrichmentService(
        @Client HttpClient httpClient,
        ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AiEnrichmentResponse> enrich(AiEnrichmentRequest request) {
        if(apiKey == null || apiKey.isBlank() || apiUrl == null || apiUrl.isBlank()) {
            log.warn("Foundry API key or URL not configured — skipping AI enrichment");
            return Optional.empty();
        }
        try {
            String prompt = buildPrompt(request);
            
            Map<String, Object> body = new HashMap<>(Map.of(
                "messages", List.of(
                    Map.of("role", "system", "content", "You are a multilingual technical content specialist for a software conference platform."),
                    Map.of("role", "user", "content", prompt)
                ),
                "response_format", Map.of("type", "json_object")
            ));

            if (model != null && !model.isBlank()) {
                body.put("model", model);
            }

            String requestUrl = apiUrl;
            if (!requestUrl.contains("chat/completions")) {
                if (!requestUrl.endsWith("/")) {
                    requestUrl += "/";
                }
                requestUrl += "chat/completions";
            }

            String responseJson = httpClient.toBlocking().retrieve(
                HttpRequest.POST(requestUrl, body)
                    .contentType(MediaType.APPLICATION_JSON_TYPE)
                    .header("api-key", apiKey)
                    .header("Authorization", "Bearer " + apiKey),
                String.class);
                
            return Optional.of(parseFoundryResponse(responseJson));
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

    private AiEnrichmentResponse parseFoundryResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        String text = root
            .path("choices").get(0)
            .path("message")
            .path("content").asText();
        return objectMapper.readValue(text, AiEnrichmentResponse.class);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
