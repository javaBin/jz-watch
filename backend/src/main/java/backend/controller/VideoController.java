package backend.controller;

import backend.controller.dto.SpeakerResponse;
import backend.controller.dto.VideoResponse;
import backend.domain.Video;
import backend.repository.SpeakerRow;
import backend.repository.VideoImpressionRepository;
import backend.repository.VideoRepository;
import backend.repository.VideoSpeakerRepository;
import backend.repository.VideoViewCount;
import backend.service.VideoTrackingService;
import backend.util.HttpRequestUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.transaction.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Controller("/api/videos")
@ExecuteOn(TaskExecutors.BLOCKING)
@Transactional
public class VideoController {

    private static final String VIMEO_EMBED = "https://player.vimeo.com/video/";

    private final VideoRepository videoRepository;
    private final VideoSpeakerRepository videoSpeakerRepository;
    private final VideoImpressionRepository impressionRepository;
    private final VideoTrackingService trackingService;

    public VideoController(VideoRepository videoRepository,
            VideoSpeakerRepository videoSpeakerRepository,
            VideoImpressionRepository impressionRepository,
            VideoTrackingService trackingService) {
        this.videoRepository = videoRepository;
        this.videoSpeakerRepository = videoSpeakerRepository;
        this.impressionRepository = impressionRepository;
        this.trackingService = trackingService;
    }

    /** Paginated list of all videos, sorted by (base_score + view_boost) DESC. */
    @Get
    public List<VideoResponse> listVideos(
            @QueryValue(defaultValue = "0") int page,
            @QueryValue(defaultValue = "20") int size,
            @QueryValue(defaultValue = "popularity") String sort,
            @QueryValue String lang,
            HttpRequest<?> request) {

        int limitedSize = Math.min(size, 20);
        int offset = page * limitedSize;
        List<Video> videos = switch (sort) {
            case "name_asc" -> videoRepository.listVideosByNameAsc(limitedSize, offset);
            case "name_desc" -> videoRepository.listVideosByNameDesc(limitedSize, offset);
            case "date_asc" -> videoRepository.listVideosByDateAsc(limitedSize, offset);
            case "date_desc" -> videoRepository.listVideosByDateDesc(limitedSize, offset);
            default -> videoRepository.listVideos(limitedSize, offset);
        };
        List<VideoResponse> responses = toResponses(videos, null, null, lang);
        trackImpressions(responses, "list", request);
        return responses;
    }

    /** Single video detail with related videos. */
    @Get("/{id}")
    public Optional<VideoResponse> getVideo(
            String id,
            @QueryValue String lang,
            HttpRequest<?> request) {
        Optional<Video> found = videoRepository.findById(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Video video = found.get();
        List<Video> related = videoRepository.findRelated(id);

        List<String> allIds = collectIds(List.of(video), related);
        Map<String, List<SpeakerRow>> speakerMap = loadSpeakers(allIds);
        Map<String, Long> viewCounts = loadViewCounts(allIds);

        VideoResponse videoResponse = toResponse(video, speakerMap.get(video.getId()), null, lang,
                viewCounts.getOrDefault(video.getId(), 0L));
        List<VideoResponse> relatedResponses = toResponses(related, speakerMap, viewCounts, lang);

        String ip = HttpRequestUtils.extractIp(request);
        String ua = HttpRequestUtils.extractUserAgent(request);
        trackingService.trackDetailImpression(video.getId(), video.getLengthMinutes(), ip, ua);
        trackingService.trackImpressions(relatedResponses.stream().map(VideoResponse::id).collect(Collectors.toList()),
                "related", ip, ua);

        return Optional.of(withRelated(videoResponse, relatedResponses));
    }

    /**
     * Full-text search.
     *
     * @param q    search query
     * @param lang "en" or "no" — defaults to searching both vectors
     */
    @Get("/search")
    public List<VideoResponse> search(
            @QueryValue String q,
            @QueryValue String lang,
            @QueryValue(defaultValue = "0") int page,
            @QueryValue(defaultValue = "20") int size,
            HttpRequest<?> request) {

        int limitedSize = Math.min(size, 50);
        int offset = page * limitedSize;

        List<Video> videos = switch (lang) {
            case "en" -> videoRepository.searchEn(q, limitedSize, offset);
            case "no" -> videoRepository.searchNo(q, limitedSize, offset);
            default -> videoRepository.searchBoth(q, limitedSize, offset);
        };
        List<VideoResponse> responses = toResponses(videos, null, null, lang);
        trackImpressions(responses, "search", request);

        String ip = HttpRequestUtils.extractIp(request);
        String ua = HttpRequestUtils.extractUserAgent(request);
        trackingService.trackSearch(q, responses.size(), ip, ua);

        return responses;
    }

    /** Videos with the most unique viewers in the last 7 days. */
    @Get("/trending")
    public List<VideoResponse> trending(
            @QueryValue String lang,
            @QueryValue(defaultValue = "0") int page,
            @QueryValue(defaultValue = "20") int size,
            HttpRequest<?> request) {
        int limitedSize = Math.min(size, 50);
        List<Video> videos = videoRepository.findTrending(limitedSize, page * limitedSize);
        List<VideoResponse> responses = toResponses(videos, null, null, lang);
        trackImpressions(responses, "trending", request);
        return responses;
    }

    /** Exact tag match for keywords or AI keywords. */
    @Get("/tag")
    public List<VideoResponse> tag(
            @QueryValue String tag,
            @QueryValue String lang,
            @QueryValue(defaultValue = "0") int page,
            @QueryValue(defaultValue = "20") int size,
            HttpRequest<?> request) {
        int limitedSize = Math.min(size, 50);
        List<Video> videos = videoRepository.findByTag(tag, limitedSize, page * limitedSize);
        List<VideoResponse> responses = toResponses(videos, null, null, lang);
        trackImpressions(responses, "tagsearch", request);
        return responses;
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private List<VideoResponse> toResponses(List<Video> videos, Map<String, List<SpeakerRow>> speakerMap,
            Map<String, Long> viewCounts, String lang) {
        if (videos.isEmpty()) {
            return List.of();
        }
        List<String> ids = videos.stream().map(Video::getId).collect(Collectors.toList());
        Map<String, List<SpeakerRow>> speakers = speakerMap != null ? speakerMap : loadSpeakers(ids);
        Map<String, Long> counts = viewCounts != null ? viewCounts : loadViewCounts(ids);
        return videos.stream()
                .map(v -> toResponse(v, speakers.get(v.getId()), null, lang, counts.getOrDefault(v.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private VideoResponse toResponse(Video v, List<SpeakerRow> speakers, List<VideoResponse> related, String lang,
            long viewCount) {
        List<SpeakerResponse> speakerResponses = speakers == null ? List.of()
                : speakers.stream()
                        .map(s -> new SpeakerResponse(s.name(), s.bio()))
                        .collect(Collectors.toList());

        int base = v.getBaseScore() != null ? v.getBaseScore() : 0;
        int boost = v.getViewBoost() != null ? v.getViewBoost() : 0;

        String title = switch (lang) {
            case "en" -> v.getTitleEn() != null ? v.getTitleEn() : v.getTitle();
            case "no" -> v.getTitleNo() != null ? v.getTitleNo() : v.getTitle();
            default -> v.getTitle();
        };
        String abstractText = switch (lang) {
            case "en" -> v.getAbstractEn() != null ? v.getAbstractEn() : v.getAbstractText();
            case "no" -> v.getAbstractNo() != null ? v.getAbstractNo() : v.getAbstractText();
            default -> v.getAbstractText();
        };

        return new VideoResponse(
                v.getId(),
                v.getConferenceId(),
                title,
                v.getTitleEn(),
                v.getTitleNo(),
                abstractText,
                v.getAbstractEn(),
                v.getAbstractNo(),
                v.getIntendedAudience(),
                v.getVimeoId(),
                v.getVimeoId() != null ? VIMEO_EMBED + v.getVimeoId() : null,
                v.getLanguage(),
                v.getFormat(),
                v.getLengthMinutes(),
                v.getRoom(),
                v.getStartTime() != null ? v.getStartTime().toString() : null,
                v.getEndTime() != null ? v.getEndTime().toString() : null,
                v.getKeywords() != null ? v.getKeywords() : List.of(),
                v.getAiKeywords() != null ? v.getAiKeywords() : List.of(),
                base,
                boost,
                base + boost,
                v.getThumbnailUrl(),
                v.getVimeoDuration(),
                viewCount,
                speakerResponses,
                related);
    }

    private Map<String, Long> loadViewCounts(List<String> videoIds) {
        if (videoIds.isEmpty()) {
            return Map.of();
        }
        return impressionRepository.countDetailViewsForVideos(videoIds)
                .stream()
                .collect(Collectors.toMap(VideoViewCount::videoId, VideoViewCount::viewCount));
    }

    private Map<String, List<SpeakerRow>> loadSpeakers(List<String> videoIds) {
        if (videoIds.isEmpty()) {
            return Map.of();
        }
        return videoSpeakerRepository.findSpeakersForVideoIds(videoIds)
                .stream()
                .collect(Collectors.groupingBy(SpeakerRow::videoId));
    }

    private List<String> collectIds(List<Video> primary, List<Video> secondary) {
        List<String> ids = new ArrayList<>();
        primary.forEach(v -> ids.add(v.getId()));
        secondary.forEach(v -> ids.add(v.getId()));
        return ids;
    }

    private void trackImpressions(List<VideoResponse> responses, String endpoint, HttpRequest<?> request) {
        if (responses.isEmpty()) {
            return;
        }
        trackingService.trackImpressions(responses.stream().map(VideoResponse::id).collect(Collectors.toList()),
                endpoint, HttpRequestUtils.extractIp(request), HttpRequestUtils.extractUserAgent(request));
    }

    private static VideoResponse withRelated(VideoResponse v, List<VideoResponse> related) {
        return new VideoResponse(
                v.id(), v.conferenceId(), v.title(), v.titleEn(), v.titleNo(),
                v.abstractText(), v.abstractEn(), v.abstractNo(),
                v.intendedAudience(), v.vimeoId(), v.embedUrl(),
                v.language(), v.format(), v.lengthMinutes(), v.room(),
                v.startTime(), v.endTime(),
                v.keywords(), v.aiKeywords(),
                v.baseScore(), v.viewBoost(), v.totalScore(),
                v.thumbnailUrl(), v.duration(),
                v.viewCount(),
                v.speakers(), related);
    }
}
