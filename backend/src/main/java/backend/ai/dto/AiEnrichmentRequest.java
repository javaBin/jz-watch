package backend.ai.dto;

import backend.importer.dto.SleepingPillSession;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Data we send to Gemini for one video. */
public record AiEnrichmentRequest(
        String title,
        String abstractText,
        String intendedAudience,
        String suggestedKeywords,
        List<String> speakerBios,
        List<String> sourceKeywords) {

    public static AiEnrichmentRequest from(SleepingPillSession session){
        return new AiEnrichmentRequest(
            session.title(),
            session.abstractText(),
            session.intendedAudience(),
            session.suggestedKeywords(),
            speakerBios(session),
            session.suggestedKeywords() != null ? Arrays.asList(session.suggestedKeywords().split(",")) : null
        );
    }

    private static List<String> speakerBios(SleepingPillSession s) {
        if(s.speakers() == null)
            return List.of();
        return s.speakers().stream()
            .map(sp -> sp.bio() != null ? sp.bio() : "")
            .filter(b -> !b.isBlank())
            .collect(Collectors.toList());
    }
}
