package backend.controller;

import backend.domain.Video;
import backend.repository.*;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Controller("/api/stats")
@ExecuteOn(TaskExecutors.BLOCKING)
@Transactional
public class StatsController {

    private final VideoRepository videoRepository;
    private final VideoEventRepository eventRepository;
    private final VideoImpressionRepository impressionRepository;
    private final SearchLogRepository searchLogRepository;
    private final TagSearchRepository tagSearchRepository;

    public StatsController(VideoRepository videoRepository,
                           VideoEventRepository eventRepository,
                           VideoImpressionRepository impressionRepository,
                           SearchLogRepository searchLogRepository,
                           TagSearchRepository tagSearchRepository) {
        this.videoRepository = videoRepository;
        this.eventRepository = eventRepository;
        this.impressionRepository = impressionRepository;
        this.searchLogRepository = searchLogRepository;
        this.tagSearchRepository = tagSearchRepository;
    }

    @Serdeable
    public record SiteStats(
        // Row 1
        long totalVideos,
        long videosMissingVimeo,
        long videosMissingAiEnrichment,
        // Row 2
        long totalClicks,
        long totalRelatedClicks,
        long totalSearchExploreClicks,
        // Row 3
        long totalViews,
        long totalSearches,
        long totalTagClicks
    ) {}

    @Serdeable
    public record StatsVideoResponse(
        String id,
        String title,
        String vimeoOembedData,
        String sleepingPillData,
        String titleEn,
        String abstractEn,
        String titleNo,
        String abstractNo,
        List<String> aiKeywordsEn,
        List<String> aiKeywordsNo
    ) {}

    @Serdeable
    public record SearchLogResponse(
        String query,
        Integer resultsCount,
        String createdAt
    ) {}

    @Get
    public SiteStats getStats() {
        return new SiteStats(
            videoRepository.count(),
            videoRepository.countMissingVimeoData(),
            videoRepository.countMissingAiEnrichment(),
            impressionRepository.countTotalClicks(),
            impressionRepository.countTotalRelatedClicks(),
            impressionRepository.countTotalSearchExploreClicks(),
            eventRepository.countTotalViews(),
            searchLogRepository.count(),
            tagSearchRepository.count()
        );
    }

    @Get("/missing-vimeo")
    public List<StatsVideoResponse> getMissingVimeo(
        @io.micronaut.http.annotation.QueryValue(defaultValue = "10") int limit,
        @io.micronaut.http.annotation.QueryValue(defaultValue = "0") int offset
    ) {
        return videoRepository.findMissingVimeoData(limit, offset).stream()
            .map(this::toStatsResponse)
            .collect(Collectors.toList());
    }

    @Get("/missing-ai")
    public List<StatsVideoResponse> getMissingAi(
        @io.micronaut.http.annotation.QueryValue(defaultValue = "10") int limit,
        @io.micronaut.http.annotation.QueryValue(defaultValue = "0") int offset
    ) {
        return videoRepository.findMissingAiEnrichment(limit, offset).stream()
            .map(this::toStatsResponse)
            .collect(Collectors.toList());
    }

    @Get("/views")
    public List<VideoEventRepository.VideoViewCount> getVideosByViews(
        @io.micronaut.http.annotation.QueryValue(defaultValue = "10") int limit,
        @io.micronaut.http.annotation.QueryValue(defaultValue = "0") int offset
    ) {
        return eventRepository.findTopVideosByViews(limit, offset);
    }

    @Get("/clicks")
    public List<VideoImpressionRepository.VideoImpressionCount> getVideosByClicks(
        @io.micronaut.http.annotation.QueryValue(defaultValue = "10") int limit,
        @io.micronaut.http.annotation.QueryValue(defaultValue = "0") int offset
    ) {
        return impressionRepository.findTopVideosByEndpoint("detail", limit, offset);
    }

    @Get("/related-clicks")
    public List<VideoImpressionRepository.VideoImpressionCount> getVideosByRelatedClicks(
        @io.micronaut.http.annotation.QueryValue(defaultValue = "10") int limit,
        @io.micronaut.http.annotation.QueryValue(defaultValue = "0") int offset
    ) {
        return impressionRepository.findTopVideosByEndpoint("related", limit, offset);
    }

    @Get("/search-explore-clicks")
    public List<VideoImpressionRepository.VideoImpressionCount> getVideosBySearchExploreClicks(
        @io.micronaut.http.annotation.QueryValue(defaultValue = "10") int limit,
        @io.micronaut.http.annotation.QueryValue(defaultValue = "0") int offset
    ) {
        return impressionRepository.findTopVideosBySearchExplore(limit, offset);
    }

    @Get("/searches")
    public List<SearchLogResponse> getSearches(
        @io.micronaut.http.annotation.QueryValue(defaultValue = "10") int limit,
        @io.micronaut.http.annotation.QueryValue(defaultValue = "0") int offset
    ) {
        return ((List<backend.domain.SearchLog>) searchLogRepository.findAll()).stream()
            .skip(offset)
            .limit(limit)
            .map(s -> new SearchLogResponse(s.getQuery(), s.getResultsCount(), s.getCreatedAt() != null ? s.getCreatedAt().toString() : null))
            .collect(Collectors.toList());
    }

    @Get("/tag-clicks")
    public List<TagSearchRepository.TagCount> getTagClicks(
        @io.micronaut.http.annotation.QueryValue(defaultValue = "10") int limit,
        @io.micronaut.http.annotation.QueryValue(defaultValue = "0") int offset
    ) {
        return tagSearchRepository.findTopTags(limit, offset);
    }

    private StatsVideoResponse toStatsResponse(Video v) {
        return new StatsVideoResponse(
            v.getId(),
            v.getTitle(),
            v.getVimeoOembedData(),
            v.getSleepingPillData(),
            v.getTitleEn(),
            v.getAbstractEn(),
            v.getTitleNo(),
            v.getAbstractNo(),
            v.getAiKeywordsEn(),
            v.getAiKeywordsNo()
        );
    }
}
