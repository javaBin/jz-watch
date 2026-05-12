package backend.ai;

import backend.ai.dto.AiEnrichedContent;
import backend.ai.dto.AiEnrichmentRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
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
@Requires(property = "ai.provider", value = "openai")
public class OpenAiEnrichmentService implements AiEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEnrichmentService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.url:}")
    private String apiUrl;

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.model:}")
    private String model;

    public OpenAiEnrichmentService(
        @Client HttpClient httpClient,
        ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AiEnrichedContent> enrich(AiEnrichmentRequest request, String targetLanguage) {
        if (apiUrl == null || apiUrl.isBlank()) {
            log.warn("OpenAI API URL not configured — skipping AI enrichment");
            return Optional.empty();
        }
        try {
            String prompt = buildPrompt(request, targetLanguage);
            
            Map<String, Object> body = new HashMap<>(Map.of(
                "messages", List.of(
                    Map.of("role", "system", "content", "You are a technical content specialist for a software conference platform."),
                    Map.of("role", "user", "content", prompt)
                ),
                "stream", false,
                "response_format", Map.of("type", "json_object")
            ));
            log.debug("OpenAI Request ({}): Model={}, Prompt={}", targetLanguage, model, prompt);

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

            MutableHttpRequest<Map<String, Object>> httpRequest = HttpRequest.POST(requestUrl, body)
                .contentType(MediaType.APPLICATION_JSON_TYPE);
            
            if (apiKey != null && !apiKey.isBlank()) {
                httpRequest.header("Authorization", "Bearer " + apiKey)
                           .header("api-key", apiKey); // For Foundry/Azure compatibility
            }

            String responseJson = httpClient.toBlocking().retrieve(httpRequest, String.class);
            log.debug("OpenAI Raw Response: {}", responseJson);
                
            return Optional.of(parseOpenAiResponse(responseJson));
        } catch(Exception e) {
            log.error("OpenAI enrichment ({}) failed for title='{}': {}", targetLanguage, request.title(), e.getMessage());
            return Optional.empty();
        }
    }

    private String buildPrompt(AiEnrichmentRequest req, String targetLanguage) {
        String langName = targetLanguage.equalsIgnoreCase("no") ? "Norwegian" : "English";
        boolean isTranslationOnly = req.sourceKeywords() != null && !req.sourceKeywords().isEmpty();

        StringBuilder input = new StringBuilder();
        input.append("TITLE: ").append(nullToEmpty(req.title())).append("\n\n");
        input.append("ABSTRACT: ").append(nullToEmpty(req.abstractText())).append("\n\n");
        input.append("INTENDED AUDIENCE: ").append(nullToEmpty(req.intendedAudience())).append("\n\n");
        
        if (req.suggestedKeywords() != null && !req.suggestedKeywords().isBlank()) {
            input.append("SUGGESTED KEYWORDS: ").append(req.suggestedKeywords()).append("\n\n");
        }
        
        if (req.speakerBios() != null && !req.speakerBios().isEmpty()) {
            input.append("SPEAKER BIOS:\n").append(String.join("\n---\n", req.speakerBios())).append("\n\n");
        }

        if (isTranslationOnly) {
            input.append("SOURCE KEYWORDS TO TRANSLATE: ").append(String.join(", ", req.sourceKeywords())).append("\n\n");
        }

        String keywordInstruction;
        if (isTranslationOnly) {
            keywordInstruction = String.format("""
                2. TRANSLATE the 'SOURCE KEYWORDS TO TRANSLATE' list into %s.
                   Maintain a strict 1-to-1 mapping. Each translated keyword must correspond to the source keyword at the same index.""", 
                   langName);
        } else {
            keywordInstruction = String.format("""
                2. Select 5-8 highly relevant search keywords in %s.
                   - Pick only keywords that actually describe the talk's content.
                   - If (and only if) the talk covers these specific broad topics, you may include: Java, Kotlin, AI, Cloud, Microservices, Security, Frontend, Backend, DevOps, Architecture, Mobile, Data, Testing.
                   - Prefer single-word or short 2-word terms.""", 
                   langName);
        }

        return String.format("""
            You are an expert technical editor for a software conference.
            Your task is to enrich and translate talk metadata.
            
            TASKS:
            1. Regardless of the input language, provide a high-quality, professional translation and rewrite of the Title, Abstract, and Intended Audience into %s.
            %s
            
            INPUT DATA:
            %s
            
            RESPONSE FORMAT:
            Respond ONLY with a valid JSON object matching this exact schema:
            {
              "title": "...",
              "abstract": "...",
              "intendedAudience": "...",
              "aiKeywords": ["keyword1", "keyword2", ...]
            }
            """, langName, keywordInstruction, input.toString());
    }

    private AiEnrichedContent parseOpenAiResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        String text = root
            .path("choices").get(0)
            .path("message")
            .path("content").asText();
        log.debug("Extracted AI Content: {}", text);
        
        // Clean up markdown if present
        String cleaned = text;
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();
            log.debug("Cleaned AI Content: {}", cleaned);
        }
        
        return objectMapper.readValue(cleaned, AiEnrichedContent.class);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
