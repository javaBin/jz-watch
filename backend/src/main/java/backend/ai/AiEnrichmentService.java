package backend.ai;

import backend.ai.dto.AiEnrichmentRequest;
import backend.ai.dto.AiEnrichmentResponse;
import java.util.Optional;

public interface AiEnrichmentService {
    Optional<AiEnrichmentResponse> enrich(AiEnrichmentRequest request);
}
