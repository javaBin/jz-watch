package backend.service;

import backend.domain.SearchLog;
import backend.domain.VideoImpression;
import backend.repository.SearchLogRepository;
import backend.repository.VideoImpressionRepository;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.Async;

import java.time.Duration;
import java.time.Instant;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
@Transactional(Transactional.TxType.REQUIRES_NEW)
public class VideoTrackingService {

    private static final Logger log = LoggerFactory.getLogger(VideoTrackingService.class);

    private final VideoImpressionRepository impressionRepository;
    private final SearchLogRepository searchLogRepository;
    private final backend.repository.TagSearchRepository tagSearchRepository;

    public VideoTrackingService(VideoImpressionRepository impressionRepository,
            SearchLogRepository searchLogRepository,
            backend.repository.TagSearchRepository tagSearchRepository) {
        this.impressionRepository = impressionRepository;
        this.searchLogRepository = searchLogRepository;
        this.tagSearchRepository = tagSearchRepository;
    }

    /**
     * Asynchronously logs that a set of videos was surfaced to a user.
     *
     * @param videoIds  The list of video IDs that were displayed.
     * @param endpoint  The identifier for where they were shown (e.g. "list",
     *                  "search", "trending").
     * @param ipAddress The client IP address (may be null).
     * @param userAgent The client User-Agent header (may be null).
     */
    @Async(TaskExecutors.BLOCKING)
    public void trackImpressions(List<String> videoIds, String endpoint, String ipAddress, String userAgent) {
        if (videoIds == null || videoIds.isEmpty()) {
            return;
        }

        try {
            List<VideoImpression> impressions = videoIds.stream()
                    .map(id -> new VideoImpression(id, endpoint, ipAddress, userAgent))
                    .collect(Collectors.toList());

            impressionRepository.saveAll(impressions);
            log.debug("Tracked {} impressions for endpoint '{}'", videoIds.size(), endpoint);
        } catch (Exception e) {
            log.error("Failed to track video impressions", e);
        }
    }

    /**
     * Asynchronously logs that a specific video was viewed, with rate-limiting to
     * prevent
     * refreshing from artificially boosting the count.
     */
    @Async(TaskExecutors.BLOCKING)
    public void trackDetailImpression(String videoId, Integer talkLengthMinutes, String ipAddress, String userAgent) {
        if (videoId == null || videoId.isBlank()) {
            return;
        }

        try {
            // Default to 120 minutes if length is unknown
            int durationMinutes = (talkLengthMinutes != null && talkLengthMinutes > 0) ? talkLengthMinutes : 120;
            Instant since = Instant.now().minus(Duration.ofMinutes(durationMinutes));

            boolean alreadyTracked = impressionRepository
                    .existsByVideoIdAndEndpointAndIpAddressAndUserAgentAndCreatedAtGreaterThan(
                            videoId, "detail", ipAddress, userAgent, since);

            if (alreadyTracked) {
                log.debug("Skipping tracking for video '{}' from IP '{}' (already tracked within last {} mins)",
                        videoId, ipAddress, durationMinutes);
                return;
            }

            impressionRepository.save(new VideoImpression(videoId, "detail", ipAddress, userAgent));
            log.debug("Tracked new detail impression for video '{}'", videoId);
        } catch (Exception e) {
            log.error("Failed to track detail impression for video '{}'", videoId, e);
        }
    }

    /**
     * Asynchronously logs a search query.
     */
    @Async(TaskExecutors.BLOCKING)
    public void trackSearch(String query, int resultsCount, String ipAddress, String userAgent) {
        try {
            searchLogRepository.save(new SearchLog(query, resultsCount, ipAddress, userAgent, java.time.Instant.now()));
            log.debug("Tracked search query '{}' with {} results", query, resultsCount);
        } catch (Exception e) {
            log.error("Failed to track search query", e);
        }
    }

    /**
     * Asynchronously logs a tag search.
     */
    @Async(TaskExecutors.BLOCKING)
    public void trackTagSearch(String tag, String ipAddress, String userAgent) {
        try {
            tagSearchRepository.save(new backend.domain.TagSearch(tag, ipAddress, userAgent, java.time.Instant.now()));
            log.debug("Tracked tag search '{}'", tag);
        } catch (Exception e) {
            log.error("Failed to track tag search", e);
        }
    }
}
