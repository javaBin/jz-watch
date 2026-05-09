package backend.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.types.files.StreamedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

@Controller
public class SpaController {

    private static final Logger log = LoggerFactory.getLogger(SpaController.class);

    @Get(value = "/{path:^(?!api/).*$}", produces = MediaType.TEXT_HTML)
    public HttpResponse<?> spa(String path) {
        log.debug("Defaulting to SPA route: {}", path);
        InputStream index = getClass().getResourceAsStream("/public/index.html");
        if (index == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(new StreamedFile(index, MediaType.TEXT_HTML_TYPE));
    }
}
