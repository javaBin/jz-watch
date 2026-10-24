package backend.controller;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.types.files.StreamedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

@Controller
public class SpaController {

    private static final Logger log = LoggerFactory.getLogger(SpaController.class);

    @Get(value = "/", produces = MediaType.TEXT_HTML)
    public HttpResponse<?> index() {
        InputStream index = getClass().getResourceAsStream("/public/index.html");
        if (index == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(new StreamedFile(index, MediaType.TEXT_HTML_TYPE));
    }

    @Error(status = HttpStatus.NOT_FOUND, global = true)
    public HttpResponse<?> notFound(HttpRequest<?> request) {
        String path = request.getPath();
        if (path.startsWith("/api")) {
            return HttpResponse.notFound();
        }

        log.debug("Path not found, serving SPA index.html for: {}", path);
        InputStream index = getClass().getResourceAsStream("/public/index.html");
        if (index == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(new StreamedFile(index, MediaType.TEXT_HTML_TYPE));
    }
}
