package backend.repository;

import backend.domain.TagSearch;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface TagSearchRepository extends CrudRepository<TagSearch, UUID> {

    @Serdeable
    public record TagCount(String tag, long count) {}

    @Query("SELECT tag, COUNT(*) as count FROM tag_searches GROUP BY tag ORDER BY count DESC LIMIT :limit OFFSET :offset")
    List<TagCount> findTopTags(int limit, int offset);
}
