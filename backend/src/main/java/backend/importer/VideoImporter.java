package backend.importer;

import backend.ai.AiEnrichmentService;
import backend.ai.dto.AiEnrichmentRequest;
import backend.ai.dto.AiEnrichmentResponse;
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
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
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
                .sorted(Comparator.comparingInt(c -> extractYear(c.name())))
                .toList();
        int total = sorted.size();

        for (int i = 0; i < total; i++) {
            ConferenceRef confRef = sorted.get(i);
            int rank = total - i;
            int baseScore = Math.max(0, 150 - (rank - 1) * 10);
            int year = extractYear(confRef.name());

            if (importYearOnly != null && !Objects.equals(importYearOnly, year))
                continue;

            Optional<Conference> optConf = conferenceRepository.findById(confRef.id());
            if (!force && optConf.isPresent()) {
                Conference c = optConf.get();
                boolean fullyImported = c.getImportedVideos() != null && c.getTotalVideos() != null && 
                                       c.getImportedVideos().equals(c.getTotalVideos()) && 
                                       c.getImportedVideos() > 0;
                
                if (fullyImported || Boolean.TRUE.equals(c.getCompleted())) {
                    log.info("VideoImporter: skipping {}, already fully imported ({}/{})", 
                        confRef.slug(), c.getImportedVideos(), c.getTotalVideos());
                    summary.put(confRef.slug(), 0);
                    continue;
                }
            }

            try {
                int count = importConference(confRef, year, baseScore, force);
                summary.put(confRef.slug(), count);
            } catch (Exception e) {
                log.error("Failed to import conference {}: {}", confRef.slug(), e.getMessage());
            }
        }

        log.info("VideoImporter: import complete. Summary: {}", summary);
        return summary;
    }

    protected int importConference(ConferenceRef confRef, int year, int baseScore, boolean force) {
        List<SleepingPillSession> sessions;
        try {
            sessions = sleepingPillClient.getSessions(confRef.slug()).sessions();
        } catch (Exception e) {
            log.warn("Failed to fetch sessions for {}: {}", confRef.slug(), e.getMessage());
            throw new RuntimeException("Failed to fetch sessions", e);
        }

        long totalEligible = sessions.stream()
                .filter(s -> VimeoUtils.extractId(s.video()) != null)
                .count();

        upsertConference(confRef.id(), confRef.slug(), confRef.name(), year, (int) totalEligible);

        AtomicInteger importedCount = new AtomicInteger(0);
        Semaphore semaphore = new Semaphore(10);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = sessions.stream()
                    .map(session -> {
                        String vimeoId = VimeoUtils.extractId(session.video());
                        if (vimeoId == null || vimeoId.isBlank())
                            return null;

                        return CompletableFuture.runAsync(() -> {
                            try {
                                semaphore.acquire();
                                importVideo(session, vimeoId, confRef.id(), baseScore, force);
                                int current = importedCount.incrementAndGet();
                                updateProgress(confRef.id(), current);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } catch (Exception e) {
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

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void importVideo(SleepingPillSession session, String vimeoId, String confId, int baseScore, boolean force) {
        String videoId = session.id();
        List<String> keywords = parseKeywords(session.suggestedKeywords());
        String sessionRawJson = null;
        try {
            sessionRawJson = objectMapper.writeValueAsString(session);
        } catch (Exception e) {
            log.warn("Failed to serialize SleepingPill session {}: {}", videoId, e.getMessage());
        }

        Optional<Video> existingOpt = videoRepository.findById(videoId);
        if (!force && existingOpt.isPresent()) {
            log.debug("VideoImporter: skipping video {}, already in db", videoId);
            return;
        }
        boolean alreadyEnriched = existingOpt.map(v -> v.getTitleEn() != null).orElse(false);

        // --- AI enrichment ---
        Optional<AiEnrichmentResponse> enriched = Optional.empty();
        if (!alreadyEnriched || force) {
            AiEnrichmentRequest req = new AiEnrichmentRequest(
                    session.title(), session.abstractText(), session.intendedAudience(),
                    session.suggestedKeywords(), speakerBios(session));
            enriched = aiEnrichmentService.enrich(req);
        }

        // --- Vimeo oEmbed ---
        boolean alreadyHasVimeoData = existingOpt.map(v -> v.getThumbnailUrl() != null).orElse(false);
        String vimeoRawJson = null;
        VimeoOembedResponse vimeoData = null;
        if (!alreadyHasVimeoData || force) {
            try {
                vimeoData = vimeoClient.getOembed("https://vimeo.com/" + vimeoId);
                vimeoRawJson = objectMapper.writeValueAsString(vimeoData);
            } catch (Exception e) {
                log.warn("Failed to fetch Vimeo oEmbed for vimeoId={}: {}", vimeoId, e.getMessage());
            }
        }

        // --- Build entity ---
        Video video = existingOpt.orElseGet(Video::new);
        video.setId(videoId);
        video.setConferenceId(confId);
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

        if (existingOpt.isEmpty()) {
            video.setCreatedAt(Instant.now());
            video.setViewBoost(0);
        }

        if (enriched.isPresent()) {
            AiEnrichmentResponse res = enriched.get();
            if (res.english() != null && res.english().title() != null) {
                video.setTitleEn(res.english().title());
                video.setAbstractEn(res.english().abstractText());
                video.setIntendedAudienceEn(res.english().intendedAudience());
                video.setAiKeywords(res.english().aiKeywords());
            }
            if (res.norwegian() != null && res.norwegian().title() != null) {
                video.setTitleNo(res.norwegian().title());
                video.setAbstractNo(res.norwegian().abstractText());
                video.setIntendedAudienceNo(res.norwegian().intendedAudience());
                if (video.getAiKeywords() == null && res.norwegian().aiKeywords() != null) {
                    video.setAiKeywords(res.norwegian().aiKeywords());
                } else if (video.getAiKeywords() != null && res.norwegian().aiKeywords() != null) {
                    Set<String> merged = new LinkedHashSet<>(video.getAiKeywords());
                    merged.addAll(res.norwegian().aiKeywords());
                    video.setAiKeywords(new ArrayList<>(merged));
                }
            }
        }

        if (vimeoData != null) {
            video.setThumbnailUrl(vimeoData.thumbnailUrl());
            video.setVideoDuration(vimeoData.duration());
        }

        if (existingOpt.isPresent()) {
            videoRepository.update(video);
        } else {
            videoRepository.save(video);
        }

        if (vimeoRawJson != null) {
            videoRepository.updateVimeoOembed(videoId, vimeoRawJson);
        }

        if (sessionRawJson != null) {
            videoRepository.updateSleepingPillData(videoId, sessionRawJson);
        }

        List<SleepingPillSpeaker> speakers = session.speakers() != null ? session.speakers() : List.of();
        for (SleepingPillSpeaker speaker : speakers) {
            if (speaker.name() == null || speaker.name().isBlank())
                continue;
            String speakerId = UUID.nameUUIDFromBytes(speaker.name().toLowerCase().getBytes()).toString();
            upsertSpeaker(speakerId, speaker.name());
            upsertVideoSpeaker(videoId, speakerId, speaker.bio());
        }

        log.info("VideoImporter: imported '{}' ({})", video.getTitle(), videoId);
    }

    @Transactional
    protected void upsertConference(String id, String slug, String name, int year, int totalVideos) {
        Optional<Conference> opt = conferenceRepository.findById(id);
        if (opt.isPresent()) {
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
        if (speakerRepository.findById(id).isEmpty()) {
            speakerRepository.save(new Speaker(id, name));
        }
    }

    private void upsertVideoSpeaker(String videoId, String speakerId, String bio) {
        Optional<VideoSpeaker> opt = videoSpeakerRepository.findByVideoIdAndSpeakerId(videoId, speakerId);
        if (opt.isPresent()) {
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
        } catch (Exception e) {
            return 0;
        }
    }

    private static List<String> parseKeywords(String csv) {
        if (csv == null || csv.isBlank())
            return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static Integer parseLength(String len) {
        try {
            return Integer.parseInt(len);
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant parseInstant(String iso) {
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> speakerBios(SleepingPillSession s) {
        if (s.speakers() == null)
            return List.of();
        return s.speakers().stream()
                .map(sp -> sp.bio() != null ? sp.bio() : "")
                .filter(b -> !b.isBlank())
                .collect(Collectors.toList());
    }
}
