package backend.importer;

import backend.ai.AiEnrichmentService;
import backend.ai.dto.AiEnrichedContent;
import backend.ai.dto.AiEnrichmentRequest;
import backend.domain.Conference;
import backend.domain.Speaker;
import backend.domain.Video;
import backend.domain.VideoSpeaker;
import backend.importer.dto.ConferenceRef;
import backend.importer.dto.SleepingPillSession;
import backend.importer.dto.SleepingPillSpeaker;
import backend.importer.dto.VimeoOembedResponse;
import backend.repository.ConferenceRepository;
import backend.repository.SpeakerRepository;
import backend.repository.VideoRepository;
import backend.repository.VideoSpeakerRepository;
import backend.util.VimeoUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.Async;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Singleton
public class VideoImporter {

    private static final Logger log = LoggerFactory.getLogger(VideoImporter.class);

    private final SleepingPillClient sleepingPillClient;
    private final VimeoClient vimeoClient;
    private final ObjectMapper objectMapper;
    private final AiEnrichmentService aiEnrichmentService;
    private final ConferenceRepository conferenceRepository;
    private final SpeakerRepository speakerRepository;
    private final VideoRepository videoRepository;
    private final VideoSpeakerRepository videoSpeakerRepository;

    public VideoImporter(SleepingPillClient sleepingPillClient,
                         VimeoClient vimeoClient,
                         ObjectMapper objectMapper,
                         AiEnrichmentService aiEnrichmentService,
                         ConferenceRepository conferenceRepository,
                         SpeakerRepository speakerRepository,
                         VideoRepository videoRepository,
                         VideoSpeakerRepository videoSpeakerRepository) {
        this.sleepingPillClient = sleepingPillClient;
        this.vimeoClient = vimeoClient;
        this.objectMapper = objectMapper;
        this.aiEnrichmentService = aiEnrichmentService;
        this.conferenceRepository = conferenceRepository;
        this.speakerRepository = speakerRepository;
        this.videoRepository = videoRepository;
        this.videoSpeakerRepository = videoSpeakerRepository;
    }

    /**
     * Full import run. Should be triggered only via GET /api/import.
     *
     * @param force When true, re-imports all conferences regardless of completed
     *              flag,
     *              re-runs AI enrichment, and re-fetches Vimeo oEmbed data.
     * @return summary map with counts per conference
     */
    public Map<String, Integer> importAll(boolean force, Integer importYearOnly) {
        log.info("VideoImporter: starting import (force={})", force);
        Map<String, Integer> summary = new LinkedHashMap<>();

        List<ConferenceRef> conferences = sleepingPillClient.getAllConferences().conferences();
        List<ConferenceRef> sorted = conferences.stream()
            .sorted(Comparator.comparingInt((ConferenceRef c) -> extractYear(c.name())).reversed())
            .toList();
        int total = sorted.size();

        for(int i = 0; i < total; i++) {
            ConferenceRef confRef = sorted.get(i);
            int year = extractYear(confRef.name());
            int baseScore = (year - 2000) * 10;

            if(importYearOnly != null && !Objects.equals(importYearOnly, year))
                continue;

            Optional<Conference> optConf = conferenceRepository.findById(confRef.id());
            if(!force && optConf.isPresent()) {
                Conference c = optConf.get();
                boolean fullyImported = c.getImportedVideos() != null && c.getTotalVideos() != null &&
                    c.getImportedVideos().equals(c.getTotalVideos()) &&
                    c.getImportedVideos() > 0;

                if(fullyImported || Boolean.TRUE.equals(c.getCompleted())) {
                    log.info("VideoImporter: skipping {}, already fully imported ({}/{})",
                        confRef.slug(), c.getImportedVideos(), c.getTotalVideos());
                    summary.put(confRef.slug(), 0);
                    continue;
                }
            }

            try {
                int count = importConference(confRef, year, baseScore, force);
                summary.put(confRef.slug(), count);
            } catch(Exception e) {
                log.error("Failed to import conference {}: {}", confRef.slug(), e.getMessage());
            }
        }

        log.info("VideoImporter: import complete. Summary: {}", summary);
        return summary;
    }

    public int reImportVideoVimeo(String videoId) {
        Optional<Video> optVideo = videoRepository.findById(videoId);
        if(optVideo.isEmpty()) return 0;
        Video video = optVideo.get();
        if(video.getVimeoId() == null || video.getVimeoId().isBlank()) return 0;

        String vimeoUrl = "https://vimeo.com/" + video.getVimeoId();
        try {
            Optional<VimeoOembedResponse> optVimeo = vimeoClient.getOembed(vimeoUrl);
            if(optVimeo.isPresent()) {
                updateVideoVimeoData(videoId, optVimeo.get());
                return 1;
            }
        } catch(Exception e) {
            log.warn("Failed to fetch Vimeo oEmbed for video {}: {}", video.getId(), e.getMessage());
        }
        return 0;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void updateVideoVimeoData(String videoId, VimeoOembedResponse vimeoData) {
        videoRepository.findById(videoId).ifPresent(video -> {
            try {
                video.setThumbnailUrl(vimeoData.thumbnailUrl());
                video.setVideoDuration(vimeoData.duration());
                video.setVimeoOembedData(objectMapper.writeValueAsString(vimeoData));
                video.setUpdatedAt(Instant.now());
                videoRepository.update(video);
            } catch(Exception e) {
                log.error("Failed to save Vimeo data for video {}: {}", videoId, e.getMessage());
            }
        });
    }

    public boolean reImportVideoAi(String videoId) {
        Optional<Video> optVideo = videoRepository.findById(videoId);
        if(optVideo.isEmpty()) return false;
        Video video = optVideo.get();
        if(video.getSleepingPillData() == null || video.getSleepingPillData().isBlank()) return false;

        try {
            SleepingPillSession session = objectMapper.readValue(video.getSleepingPillData(), SleepingPillSession.class);

            AiEnrichmentRequest aiReq = AiEnrichmentRequest.from(session);
            Optional<AiEnrichedContent> noRes = aiEnrichmentService.enrich(aiReq, "no");
            Optional<AiEnrichedContent> enRes = aiEnrichmentService.enrich(aiReq, "en");
            updateVideoAiData(videoId, noRes, enRes);
            return true;
        } catch(Exception e) {
            log.warn("Failed to re-enrich video {}: {}", videoId, e.getMessage());
        }
        return false;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void updateVideoAiData(String videoId, Optional<AiEnrichedContent> noRes, Optional<AiEnrichedContent> enRes) {
        videoRepository.findById(videoId).ifPresent(video -> {
            applyAiEnrichment(video, noRes, enRes);
            video.setUpdatedAt(Instant.now());
            videoRepository.update(video);

        });
    }

    @Async(TaskExecutors.BLOCKING)
    public void reImportMissingVimeoData() {
        int totalCount = 0;
        int pageSize = 100;
        while(true) {
            List<Video> missing = videoRepository.findMissingVimeoData(pageSize, 0);
            if(missing.isEmpty()) break;

            log.info("VideoImporter: processing batch of {} videos missing Vimeo data", missing.size());
            int batchCount = 0;
            for(Video video : missing) {
                batchCount += reImportVideoVimeo(video.getId());
            }
            totalCount += batchCount;
            if(batchCount == 0) break; // Avoid infinite loop if nothing can be processed
        }
        log.info("VideoImporter: finished bulk Vimeo re-import. Total processed: {}", totalCount);
    }

    @Async(TaskExecutors.BLOCKING)
    public void reImportMissingAiEnrichment() {
        int totalCount = 0;
        int pageSize = 50;
        Semaphore semaphore = new Semaphore(10); // Limit concurrent AI calls

        while(true) {
            List<Video> missing = videoRepository.findMissingAiEnrichment(pageSize, 0);
            if(missing.isEmpty()) break;

            log.info("VideoImporter: processing batch of {} videos missing AI enrichment", missing.size());
            AtomicInteger batchCount = new AtomicInteger(0);

            try(ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = missing.stream()
                    .map(video -> CompletableFuture.runAsync(() -> {
                        try {
                            semaphore.acquire();
                            if(reImportVideoAi(video.getId())) {
                                batchCount.incrementAndGet();
                            }
                        } catch(InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            semaphore.release();
                        }
                    }, executor))
                    .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            int count = batchCount.get();
            totalCount += count;
            if(count == 0) break; // Avoid infinite loop
        }
        log.info("VideoImporter: finished bulk AI re-enrichment. Total processed: {}", totalCount);
    }

    protected int importConference(ConferenceRef confRef, int year, int baseScore, boolean force) {
        List<SleepingPillSession> sessions;
        try {
            sessions = sleepingPillClient.getSessions(confRef.slug()).sessions();
        } catch(Exception e) {
            log.warn("Failed to fetch sessions for {}: {}", confRef.slug(), e.getMessage());
            throw new RuntimeException("Failed to fetch sessions", e);
        }

        long totalEligible = sessions.stream()
            .filter(s -> VimeoUtils.extractId(s.video()) != null)
            .count();

        upsertConference(confRef.id(), confRef.slug(), confRef.name(), year, (int) totalEligible);

        AtomicInteger importedCount = new AtomicInteger(0);
        Semaphore semaphore = new Semaphore(10);

        try(ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = sessions.stream()
                .map(session -> {
                    String vimeoId = VimeoUtils.extractId(session.video());
                    if(vimeoId == null || vimeoId.isBlank())
                        return null;

                    return CompletableFuture.runAsync(() -> {
                        try {
                            semaphore.acquire();
                            importVideo(session, vimeoId, confRef.id(), baseScore, force);
                            int current = importedCount.incrementAndGet();
                            updateProgress(confRef.id(), current);
                        } catch(InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch(Exception e) {
                            log.error("Failed to import video {}: {}", session.id(), e.getMessage());
                        } finally {
                            semaphore.release();
                        }
                    }, executor);
                })
                .filter(Objects::nonNull)
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        int finalCount = importedCount.get();
        rebuildStats(confRef.id(), (int) totalEligible, finalCount);

        log.info("VideoImporter: {} → {}/{} videos imported (force={})", confRef.slug(), finalCount, totalEligible,
            force);
        return finalCount;
    }

    private synchronized void updateProgress(String conferenceId, int count) {
        conferenceRepository.updateImportedVideos(conferenceId, count);
    }

    @Transactional
    protected void rebuildStats(String conferenceId, int totalEligible, int importedCount) {
        videoRepository.rebuildSearchVectorEn(conferenceId);
        videoRepository.rebuildSearchVectorNo(conferenceId);
        videoRepository.updateViewBoosts();

        markConferenceCompleted(conferenceId, totalEligible, importedCount);
    }

    protected void importVideo(SleepingPillSession session, String vimeoId, String confId, int baseScore, boolean force) {
        String videoId = session.id();

        // 1. Initial Check (No long-lived transaction yet)
        Optional<Video> existingOpt = videoRepository.findById(videoId);
        if(existingOpt.isPresent() && !force) {
            return;
        }

        AiEnrichmentRequest req = AiEnrichmentRequest.from(session);
        Optional<AiEnrichedContent> noRes = aiEnrichmentService.enrich(req, "no");
        Optional<AiEnrichedContent> enRes = aiEnrichmentService.enrich(req, "en");


        VimeoOembedResponse vimeoData = null;
        try {
            vimeoData = vimeoClient.getOembed("https://vimeo.com/" + vimeoId).orElse(null);
        } catch(Exception e) {
            log.warn("Failed to fetch Vimeo oEmbed for vimeoId={}: {}", vimeoId, e.getMessage());
        }

        // 3. Save Data (Brief transaction)
        saveImportedVideo(session, videoId, vimeoId, confId, baseScore, noRes, enRes, vimeoData);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void saveImportedVideo(SleepingPillSession session, String videoId, String vimeoId,
                                     String confId, int baseScore, Optional<AiEnrichedContent> noRes,
                                     Optional<AiEnrichedContent> enRes, VimeoOembedResponse vimeoData) {

        Optional<Video> existingOpt = videoRepository.findById(videoId);
        Video video = existingOpt.orElseGet(Video::new);

        List<String> keywords = parseKeywords(session.suggestedKeywords());
        String sessionRawJson = null;
        try {
            sessionRawJson = objectMapper.writeValueAsString(session);
        } catch(Exception ignored) {
        }

        video.setId(videoId);
        video.setConferenceId(confId);
        // Default base fields from session (any language)
        video.setTitle(session.title());
        video.setAbstractText(session.abstractText());
        video.setIntendedAudience(session.intendedAudience());
        video.setVimeoId(vimeoId);
        video.setLanguage(session.language());
        video.setFormat(session.format());
        video.setTalkLengthMinutes(parseLength(session.length()));
        video.setRoom(session.room());
        video.setStartTime(parseInstant(session.startTimeZulu()));
        video.setEndTime(parseInstant(session.endTimeZulu()));
        video.setKeywords(keywords);
        video.setBaseScore(baseScore);
        video.setUpdatedAt(Instant.now());
        video.setSleepingPillData(sessionRawJson);

        if(existingOpt.isEmpty()) {
            video.setCreatedAt(Instant.now());
            video.setViewBoost(0);
        }

        // Translations and keywords will be populated by AI enrichment below

        if(noRes != null && enRes != null) {
            applyAiEnrichment(video, noRes, enRes);
        }

        if(vimeoData != null) {
            video.setThumbnailUrl(vimeoData.thumbnailUrl());
            video.setVideoDuration(vimeoData.duration());
            try {
                video.setVimeoOembedData(objectMapper.writeValueAsString(vimeoData));
            } catch(Exception ignored) {
            }
        }

        if(existingOpt.isPresent()) {
            videoRepository.update(video);
        } else {
            videoRepository.save(video);
        }


        List<SleepingPillSpeaker> speakers = session.speakers() != null ? session.speakers() : List.of();
        for(SleepingPillSpeaker speaker : speakers) {
            if(speaker.name() == null || speaker.name().isBlank()) continue;
            String speakerId = UUID.nameUUIDFromBytes(speaker.name().toLowerCase().getBytes()).toString();
            upsertSpeaker(speakerId, speaker.name());
            upsertVideoSpeaker(videoId, speakerId, speaker.bio());
        }

        log.info("VideoImporter: imported '{}' ({})", video.getTitle(), videoId);
    }

    private void applyAiEnrichment(Video video, Optional<AiEnrichedContent> noRes, Optional<AiEnrichedContent> enRes) {
        if(enRes.isPresent()) {
            var res = enRes.get();
            video.setTitleEn(res.title());
            video.setAbstractEn(res.abstractText());
            video.setIntendedAudienceEn(res.intendedAudience());
            video.setAiKeywordsEn(res.aiKeywords());
        }
        if(noRes.isPresent()) {
            var res = noRes.get();
            video.setTitleNo(res.title());
            video.setAbstractNo(res.abstractText());
            video.setIntendedAudienceNo(res.intendedAudience());
            video.setAiKeywordsNo(res.aiKeywords());
        }
    }


    @Transactional
    protected void upsertConference(String id, String slug, String name, int year, int totalVideos) {
        Optional<Conference> opt = conferenceRepository.findById(id);
        if(opt.isPresent()) {
            Conference c = opt.get();
            c.setName(name);
            c.setYear(year);
            c.setTotalVideos(totalVideos);
            conferenceRepository.update(c);
        } else {
            Conference c = new Conference(id, slug, name, year);
            c.setTotalVideos(totalVideos);
            c.setImportedVideos(0);
            c.setCompleted(false);
            conferenceRepository.save(c);
        }
    }

    private void markConferenceCompleted(String id, int total, int imported) {
        conferenceRepository.findById(id).ifPresent(c -> {
            c.setCompleted(true);
            c.setTotalVideos(total);
            c.setImportedVideos(imported);
            conferenceRepository.update(c);
        });
    }

    private void upsertSpeaker(String id, String name) {
        if(speakerRepository.findById(id).isEmpty()) {
            speakerRepository.save(new Speaker(id, name));
        }
    }

    private void upsertVideoSpeaker(String videoId, String speakerId, String bio) {
        Optional<VideoSpeaker> opt = videoSpeakerRepository.findByVideoIdAndSpeakerId(videoId, speakerId);
        if(opt.isPresent()) {
            VideoSpeaker vs = opt.get();
            vs.setBio(bio);
            videoSpeakerRepository.update(vs);
        } else {
            videoSpeakerRepository.save(new VideoSpeaker(videoId, speakerId, bio));
        }
    }

    private static int extractYear(String name) {
        try {
            return Integer.parseInt(name.replaceAll("\\D", "").substring(0, 4));
        } catch(Exception e) {
            return 0;
        }
    }

    private static List<String> parseKeywords(String csv) {
        if(csv == null || csv.isBlank())
            return List.of();
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    private static Integer parseLength(String len) {
        try {
            return Integer.parseInt(len);
        } catch(Exception e) {
            return null;
        }
    }

    private static Instant parseInstant(String iso) {
        try {
            return Instant.parse(iso);
        } catch(Exception e) {
            return null;
        }
    }
}
