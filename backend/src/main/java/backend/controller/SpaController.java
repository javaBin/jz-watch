package backend.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.types.files.StreamedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URLConnection;

@Controller
public class SpaController {

    private static final Logger log = LoggerFactory.getLogger(SpaController.class);

    @Get(value = "/{path:^(?!api/).*$}")
    public HttpResponse<?> spa(String path) {
        log.debug("Routing request for path: {}", path);

        // 1. Try to serve exact static resource from classpath (/public/...)
        InputStream resource = getClass().getResourceAsStream("/public/" + path);
        if (resource != null) {
            return HttpResponse.ok(new StreamedFile(resource, resolveMediaType(path)));
        }

        // 2. If it is an asset or file with extension that was not found, return 404 (not index.html!)
        if (path.contains(".") || path.startsWith("assets/")) {
            return HttpResponse.notFound();
        }

        // 3. Otherwise, it is an SPA route (/stats, /explore, /watch/...) -> serve index.html
        InputStream index = getClass().getResourceAsStream("/public/index.html");
        if (index == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(new StreamedFile(index, MediaType.TEXT_HTML_TYPE));
    }

    @Get(value = "/", produces = MediaType.TEXT_HTML)
    public HttpResponse<?> index() {
        InputStream index = getClass().getResourceAsStream("/public/index.html");
        if (index == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(new StreamedFile(index, MediaType.TEXT_HTML_TYPE));
    }

    private MediaType resolveMediaType(String path) {
        if (path.endsWith(".js") || path.endsWith(".mjs")) {
            return new MediaType("application/javascript");
        }
        if (path.endsWith(".css")) {
            return new MediaType("text/css");
        }
        if (path.endsWith(".html")) {
            return MediaType.TEXT_HTML_TYPE;
        }
        if (path.endsWith(".svg")) {
            return new MediaType("image/svg+xml");
        }
        if (path.endsWith(".png")) {
            return MediaType.IMAGE_PNG_TYPE;
        }
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_TYPE;
        }
        if (path.endsWith(".ico")) {
            return new MediaType("image/x-icon");
        }
        if (path.endsWith(".json")) {
            return MediaType.APPLICATION_JSON_TYPE;
        }
        String mime = URLConnection.guessContentTypeFromName(path);
        return mime != null ? new MediaType(mime) : MediaType.APPLICATION_OCTET_STREAM_TYPE;
    }
}
