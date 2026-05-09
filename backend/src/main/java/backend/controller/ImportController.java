package backend.controller;

import backend.importer.VideoImporter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;
import java.util.Optional;

@Requires(env = "import")
@Controller("/api/import")
@ExecuteOn(TaskExecutors.BLOCKING)
public class ImportController {

    private final VideoImporter videoImporter;

    public ImportController(VideoImporter videoImporter) {
        this.videoImporter = videoImporter;
    }

    /**
     * Triggers a full import from SleepingPill, including AI enrichment,
     * Vimeo oEmbed fetch, search vector rebuild, and view_boost update.
     *
     * @param force When true, re-processes all conferences and videos even if already imported.
     *              Re-runs AI enrichment and re-fetches Vimeo metadata. Expensive — use sparingly.
     */
    @Get
    public Map<String, Object> runImport(
        @QueryValue(defaultValue = "false") boolean force,
        @QueryValue Optional<Integer> year
    ) {
        long start = System.currentTimeMillis();
        Map<String, Integer> summary = videoImporter.importAll(force, year.orElse(null));
        long elapsed = System.currentTimeMillis() - start;
        int total = summary.values().stream().mapToInt(i -> i).sum();
        return Map.of(
                "status", "ok",
                "force", force,
                "totalVideos", total,
                "elapsedMs", elapsed,
                "byConference", summary);
    }
}
