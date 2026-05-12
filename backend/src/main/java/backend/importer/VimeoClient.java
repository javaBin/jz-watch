package backend.importer;

import backend.importer.dto.VimeoOembedResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

import java.util.Optional;

@Client("https://vimeo.com")
public interface VimeoClient {

    /**
     * Fetches oEmbed metadata for a Vimeo video. Pass the full video URL, e.g.
     * https://vimeo.com/1115459661
     */
    @Get("/api/oembed.json{?url}")
    Optional<VimeoOembedResponse> getOembed(@QueryValue String url);
}
