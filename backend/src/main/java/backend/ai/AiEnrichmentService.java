package backend.ai;

import backend.ai.dto.AiEnrichedContent;
import backend.ai.dto.AiEnrichmentRequest;
import java.util.Optional;

public interface AiEnrichmentService {
    Optional<AiEnrichedContent> enrich(AiEnrichmentRequest request, String targetLanguage);
}
