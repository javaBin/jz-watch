package backend.repository;

import backend.domain.Video;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface VideoRepository extends CrudRepository<Video, String> {

    @Query("SELECT * FROM videos WHERE thumbnail_url IS NULL LIMIT :limit OFFSET :offset")
    List<Video> findMissingVimeoData(int limit, int offset);

    @Query("SELECT COUNT(*) FROM videos WHERE thumbnail_url IS NULL")
    long countMissingVimeoData();

    @Query("SELECT * FROM videos WHERE title_en IS NULL LIMIT :limit OFFSET :offset")
    List<Video> findMissingAiEnrichment(int limit, int offset);

    @Query("SELECT COUNT(*) FROM videos WHERE title_en IS NULL")
    long countMissingAiEnrichment();

    @Query("SELECT * FROM videos ORDER BY (base_score + view_boost) DESC LIMIT :limit OFFSET :offset")
    List<Video> listVideos(int limit, int offset);

    @Query("SELECT * FROM videos ORDER BY title ASC LIMIT :limit OFFSET :offset")
    List<Video> listVideosByNameAsc(int limit, int offset);

    @Query("SELECT * FROM videos ORDER BY title DESC LIMIT :limit OFFSET :offset")
    List<Video> listVideosByNameDesc(int limit, int offset);

    @Query("SELECT v.* FROM videos v JOIN conferences c ON v.conference_id = c.id ORDER BY c.year ASC, v.start_time ASC NULLS LAST LIMIT :limit OFFSET :offset")
    List<Video> listVideosByDateAsc(int limit, int offset);

    @Query("SELECT v.* FROM videos v JOIN conferences c ON v.conference_id = c.id ORDER BY c.year DESC, v.start_time DESC NULLS LAST LIMIT :limit OFFSET :offset")
    List<Video> listVideosByDateDesc(int limit, int offset);

    @Query("SELECT * FROM videos " +
           "WHERE search_vector_en @@ plainto_tsquery('english', :q) " +
           "ORDER BY ts_rank(search_vector_en, plainto_tsquery('english', :q)) DESC " +
           "LIMIT :limit OFFSET :offset")
    List<Video> searchEn(String q, int limit, int offset);

    @Query("SELECT * FROM videos " +
           "WHERE search_vector_no @@ plainto_tsquery('norwegian', :q) " +
           "ORDER BY ts_rank(search_vector_no, plainto_tsquery('norwegian', :q)) DESC " +
           "LIMIT :limit OFFSET :offset")
    List<Video> searchNo(String q, int limit, int offset);

    @Query("SELECT * FROM videos " +
           "WHERE search_vector_en @@ plainto_tsquery('english', :q) " +
           "   OR search_vector_no @@ plainto_tsquery('norwegian', :q) " +
           "ORDER BY GREATEST(" +
           "  ts_rank(search_vector_en, plainto_tsquery('english', :q)), " +
           "  ts_rank(search_vector_no, plainto_tsquery('norwegian', :q))) DESC " +
           "LIMIT :limit OFFSET :offset")
    List<Video> searchBoth(String q, int limit, int offset);

    @Query("SELECT * FROM videos " +
           "ORDER BY view_boost DESC, (base_score + view_boost) DESC " +
           "LIMIT :limit OFFSET :offset")
    List<Video> findTrending(int limit, int offset);

    @Query("SELECT * FROM videos WHERE :tag = ANY(keywords) OR :tag = ANY(ai_keywords_en) OR :tag = ANY(ai_keywords_no) ORDER BY (base_score + view_boost) DESC LIMIT :limit OFFSET :offset")
    List<Video> findByTag(String tag, int limit, int offset);

    @Query("SELECT v.* FROM videos v " +
           "WHERE v.id != :videoId " +
           "  AND (EXISTS (SELECT 1 FROM unnest(v.keywords) k " +
           "               WHERE k = ANY(SELECT unnest(tv.keywords) FROM videos tv WHERE tv.id = :videoId) " +
           "                  OR k = ANY(SELECT unnest(tv.ai_keywords_en) FROM videos tv WHERE tv.id = :videoId) " +
           "                  OR k = ANY(SELECT unnest(tv.ai_keywords_no) FROM videos tv WHERE tv.id = :videoId)) " +
           "       OR EXISTS (SELECT 1 FROM unnest(v.ai_keywords_en) k " +
           "               WHERE k = ANY(SELECT unnest(tv.keywords) FROM videos tv WHERE tv.id = :videoId) " +
           "                  OR k = ANY(SELECT unnest(tv.ai_keywords_en) FROM videos tv WHERE tv.id = :videoId) " +
           "                  OR k = ANY(SELECT unnest(tv.ai_keywords_no) FROM videos tv WHERE tv.id = :videoId)) " +
           "       OR EXISTS (SELECT 1 FROM unnest(v.ai_keywords_no) k " +
           "               WHERE k = ANY(SELECT unnest(tv.keywords) FROM videos tv WHERE tv.id = :videoId) " +
           "                  OR k = ANY(SELECT unnest(tv.ai_keywords_en) FROM videos tv WHERE tv.id = :videoId) " +
           "                  OR k = ANY(SELECT unnest(tv.ai_keywords_no) FROM videos tv WHERE tv.id = :videoId)) " +
           "       OR EXISTS (SELECT 1 FROM video_speakers vso " +
           "                  JOIN video_speakers vst ON vso.speaker_id = vst.speaker_id " +
           "                  WHERE vso.video_id = v.id AND vst.video_id = :videoId)) " +
           "ORDER BY (v.base_score + v.view_boost) DESC LIMIT 6")
    List<Video> findRelated(String videoId);

    @Query("UPDATE videos SET search_vector_en = (" +
            "  setweight(to_tsvector('english', coalesce(title_en, title, '')), 'A') || " +
            "  setweight(to_tsvector('simple', coalesce(array_to_string(keywords, ' '), '') || ' ' || coalesce(array_to_string(ai_keywords_en, ' '), '') || ' ' || coalesce(array_to_string(ai_keywords_no, ' '), '')), 'B') || " +
            "  setweight(to_tsvector('simple', coalesce((SELECT string_agg(sp.name, ' ') FROM video_speakers vs JOIN speakers sp ON vs.speaker_id = sp.id WHERE vs.video_id = videos.id), '')), 'B') || " +
            "  setweight(to_tsvector('english', coalesce(abstract_en, abstract, '') || ' ' || coalesce(intended_audience_en, intended_audience, '')), 'C')" +
            ") WHERE conference_id = :conferenceId")
    void rebuildSearchVectorEn(String conferenceId);

    @Query("UPDATE videos SET search_vector_no = (" +
            "  setweight(to_tsvector('norwegian', coalesce(title_no, title, '')), 'A') || " +
            "  setweight(to_tsvector('simple', coalesce(array_to_string(keywords, ' '), '') || ' ' || coalesce(array_to_string(ai_keywords_no, ' '), '') || ' ' || coalesce(array_to_string(ai_keywords_en, ' '), '')), 'B') || " +
            "  setweight(to_tsvector('simple', coalesce((SELECT string_agg(sp.name, ' ') FROM video_speakers vs JOIN speakers sp ON vs.speaker_id = sp.id WHERE vs.video_id = videos.id), '')), 'B') || " +
            "  setweight(to_tsvector('norwegian', coalesce(abstract_no, abstract, '') || ' ' || coalesce(intended_audience_no, intended_audience, '')), 'C')" +
            ") WHERE conference_id = :conferenceId")
    void rebuildSearchVectorNo(String conferenceId);

    @Query("WITH stats AS (" +
           "  SELECT v.id, COALESCE(LEAST(s.viewers_last_7d * 2, 100), 0) as new_boost " +
           "  FROM videos v " +
           "  LEFT JOIN video_view_stats s ON v.id = s.video_id" +
           ") " +
           "UPDATE videos SET view_boost = stats.new_boost " +
           "FROM stats WHERE videos.id = stats.id")
    void updateViewBoosts();
}
