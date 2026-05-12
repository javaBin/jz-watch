package backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/** One language's translated content returned by Gemini. */
@Serdeable
public record AiEnrichedContent(
        String title,
        @JsonProperty("abstract") String abstractText,
        String intendedAudience,
        List<String> aiKeywords) {
}
