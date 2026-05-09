package backend.repository;

import backend.domain.Conference;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface ConferenceRepository extends CrudRepository<Conference, String> {

    void updateTotalVideos(@Id String id, Integer totalVideos);

    void updateImportedVideos(@Id String id, Integer importedVideos);
}
