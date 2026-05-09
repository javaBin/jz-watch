package backend.controller.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record VideoResponse(
        String id,
        String conferenceId,
        String title,
        String titleEn,
        String titleNo,
        String abstractText,
        String abstractEn,
        String abstractNo,
        String intendedAudience,
        String vimeoId,
        String embedUrl, // computed: https://player.vimeo.com/video/{vimeoId}
        String language,
        String format,
        Integer talkLengthMinutes,
        String room,
        String startTime,
        String endTime,
        List<String> keywords,
        List<String> aiKeywords,
        int baseScore,
        int viewBoost,
        int totalScore,
        String thumbnailUrl,        // from Vimeo oEmbed
        Integer duration,           // video duration in seconds, from Vimeo oEmbed
        long viewCount,             // number of times GET /api/videos/{id} was called
        List<SpeakerResponse> speakers,
        List<VideoResponse> related // only populated on GET /api/videos/{id}
) {
}
