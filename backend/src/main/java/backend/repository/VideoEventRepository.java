package backend.repository;

import backend.domain.VideoEvent;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface VideoEventRepository extends CrudRepository<VideoEvent, UUID> {

    @Query("INSERT INTO video_events (video_id, user_uuid, event_type, playback_position_secs, ip_address, user_agent) " +
           "VALUES (:videoId, :userUuid, :eventType::video_event_type, :playbackPositionSecs, :ipAddress, :userAgent)")
    void trackVideoEvent(String videoId, String userUuid, String eventType,
                         @Nullable Integer playbackPositionSecs,
                         @Nullable String ipAddress,
                         @Nullable String userAgent);
    @Query("SELECT COUNT(*) FROM video_events WHERE event_type = 'play'")
    long countTotalViews();

    @io.micronaut.serde.annotation.Serdeable
    public record VideoViewCount(String videoId, String title, long count) {}

    @Query("SELECT v.id as video_id, v.title, COUNT(e.id) as count " +
           "FROM videos v JOIN video_events e ON v.id = e.video_id " +
           "WHERE e.event_type = 'play' " +
           "GROUP BY v.id, v.title " +
           "ORDER BY count DESC " +
           "LIMIT :limit OFFSET :offset")
    List<VideoViewCount> findTopVideosByViews(int limit, int offset);
}
