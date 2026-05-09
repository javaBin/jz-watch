package backend.ai;

import backend.ai.dto.AiEnrichmentRequest;
import backend.ai.dto.AiEnrichmentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import io.micronaut.http.HttpRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AiEnrichmentServiceTest {

    @Mock HttpClient httpClient;
    @Mock BlockingHttpClient blockingHttpClient;

    GeminiAiEnrichmentService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new GeminiAiEnrichmentService(httpClient, new ObjectMapper());
        setField(service, "apiKey", "test-api-key");
        setField(service, "model", "gemini-2.0-flash");
        when(httpClient.toBlocking()).thenReturn(blockingHttpClient);
    }

    @Test
    void enrichReturnsTranslatedContentForNorwegianTalk() {
        String fakeGeminiResponse = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{
                        "text": "{\\"english\\":{\\"title\\":\\"Practical Machine Learning in Java\\",\\"abstract\\":\\"Learn how to integrate ML models into Java applications using modern frameworks.\\",\\"intendedAudience\\":\\"Java developers with an interest in machine learning\\",\\"aiKeywords\\":[\\"machine learning\\",\\"MLflow\\",\\"model serving\\",\\"inference\\",\\"neural networks\\"]},\\"norwegian\\":{\\"title\\":\\"Praktisk maskinlæring i Java\\",\\"abstract\\":\\"Lær hvordan du integrerer ML-modeller i Java-applikasjoner med moderne rammeverk.\\",\\"intendedAudience\\":\\"Java-utviklere med interesse for maskinlæring\\",\\"aiKeywords\\":[\\"maskinlæring\\",\\"modell\\",\\"inferens\\",\\"nevrale nettverk\\",\\"integrasjon\\"]}}"
                      }]
                    }
                  }]
                }
                """;

        when(blockingHttpClient.retrieve(any(HttpRequest.class), eq(String.class))).thenReturn(fakeGeminiResponse);

        AiEnrichmentRequest request = new AiEnrichmentRequest(
                "Maskinlæring i Java",
                "En introduksjon til maskinlæring integrert i Java-applikasjoner.",
                "Java-utviklere",
                "machine-learning,java,ai",
                List.of("Senior Java developer with 10 years experience and ML background")
        );

        Optional<AiEnrichmentResponse> result = service.enrich(request);

        assertTrue(result.isPresent());
        AiEnrichmentResponse response = result.get();

        assertEquals("Practical Machine Learning in Java", response.english().title());
        assertNotNull(response.english().abstractText());
        assertFalse(response.english().aiKeywords().isEmpty());

        assertEquals("Praktisk maskinlæring i Java", response.norwegian().title());
        assertNotNull(response.norwegian().abstractText());
        assertFalse(response.norwegian().aiKeywords().isEmpty());
    }

    private static void setField(Object target, String name, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
