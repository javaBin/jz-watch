package backend.repository;

import backend.domain.VideoImpression;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface VideoImpressionRepository extends CrudRepository<VideoImpression, UUID> {

    @Query("SELECT video_id, COUNT(*) AS view_count " +
            "FROM video_impressions " +
            "WHERE endpoint = 'detail' AND video_id IN (:videoIds) " +
            "GROUP BY video_id")
    List<VideoViewCount> countDetailViewsForVideos(List<String> videoIds);

    boolean existsByVideoIdAndEndpointAndIpAddressAndUserAgentAndCreatedAtGreaterThan(
            String videoId, String endpoint, String ipAddress, String userAgent, java.time.Instant since);
    @Query("SELECT COUNT(*) FROM video_impressions WHERE endpoint = 'detail'")
    long countTotalClicks();

    @Query("SELECT COUNT(*) FROM video_impressions WHERE endpoint = 'related'")
    long countTotalRelatedClicks();

    @Query("SELECT COUNT(*) FROM video_impressions WHERE endpoint IN ('search', 'list', 'trending', 'tagsearch')")
    long countTotalSearchExploreClicks();

    @Serdeable
    public record VideoImpressionCount(String videoId, String title, long count) {}

    @Query("SELECT v.id as video_id, v.title, COUNT(i.id) as count " +
           "FROM videos v JOIN video_impressions i ON v.id = i.video_id " +
           "WHERE i.endpoint = :endpoint " +
           "GROUP BY v.id, v.title " +
           "ORDER BY count DESC " +
           "LIMIT :limit OFFSET :offset")
    List<VideoImpressionCount> findTopVideosByEndpoint(String endpoint, int limit, int offset);

    @Query("SELECT v.id as video_id, v.title, COUNT(i.id) as count " +
           "FROM videos v JOIN video_impressions i ON v.id = i.video_id " +
           "WHERE i.endpoint IN ('search', 'list', 'trending', 'tagsearch') " +
           "GROUP BY v.id, v.title " +
           "ORDER BY count DESC " +
           "LIMIT :limit OFFSET :offset")
    List<VideoImpressionCount> findTopVideosBySearchExplore(int limit, int offset);
}
