package backend.ai;

import backend.ai.dto.AiEnrichedContent;
import backend.ai.dto.AiEnrichmentRequest;
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
    public Optional<AiEnrichedContent> enrich(AiEnrichmentRequest request, String targetLanguage) {
        if(apiKey == null || apiKey.isBlank()) {
            log.warn("GOOGLE_API_KEY not configured — skipping AI enrichment");
            return Optional.empty();
        }
        try {
            String prompt = buildPrompt(request, targetLanguage);
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
            log.error("AI enrichment ({}) failed for title='{}': {}", targetLanguage, request.title(), e.getMessage());
            return Optional.empty();
        }
    }

    private String buildPrompt(AiEnrichmentRequest req, String targetLanguage) {
        String langName = targetLanguage.equalsIgnoreCase("no") ? "Norwegian" : "English";
        boolean isTranslationOnly = req.sourceKeywords() != null && !req.sourceKeywords().isEmpty();
        
        try {
            Map<String, Object> input = new HashMap<>();
            input.put("title", nullToEmpty(req.title()));
            input.put("abstract", nullToEmpty(req.abstractText()));
            input.put("intendedAudience", nullToEmpty(req.intendedAudience()));
            input.put("suggestedKeywords", nullToEmpty(req.suggestedKeywords()));
            input.put("speakerBios", req.speakerBios() != null ? req.speakerBios() : List.of());
            if (isTranslationOnly) {
                input.put("sourceKeywords", req.sourceKeywords());
            }

            String inputJson = objectMapper.writeValueAsString(input);
            
            String keywordInstruction;
            if (isTranslationOnly) {
                keywordInstruction = String.format("""
                    2. TRANSLATE the following sourceKeywords into %s: %s.
                       Maintain a strict 1-to-1 mapping. Each translated keyword must correspond to the source keyword at the same index.""", 
                       langName, req.sourceKeywords());
            } else {
                keywordInstruction = String.format("""
                    2. Generate a set of 5-8 search keywords in %s.
                       Each keyword must be at most 2 words, but PREFER single-word keywords (e.g., "Java", "Kubernetes", "AI").
                       If the talk covers popular topics, include relevant keywords: Java, Kotlin, AI, Cloud, Microservices, Security, Frontend, Backend, DevOps, Architecture, Mobile, Data, Testing.""", 
                       langName);
            }

            return String.format("""
                You are a technical content specialist for a software conference platform.
                
                You will receive metadata about a tech conference talk as JSON.
                Your tasks:
                1. Regardless of the input language, provide a high-quality translation and rewrite of ALL text fields into %s.
                %s
                
                Input:
                %s
                
                Respond ONLY with a valid JSON object matching this exact schema:
                {
                  "title": "...",
                  "abstract": "...",
                  "intendedAudience": "...",
                  "aiKeywords": ["keyword1", "keyword2", ...]
                }
                """, langName, keywordInstruction, inputJson);
        } catch(Exception e) {
            throw new RuntimeException("Failed to build AI prompt", e);
        }
    }

    private AiEnrichedContent parseGeminiResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        String text = root
            .path("candidates").get(0)
            .path("content").path("parts").get(0)
            .path("text").asText();
        return objectMapper.readValue(text, AiEnrichedContent.class);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
