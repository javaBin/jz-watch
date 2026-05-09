package backend.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Controller("/api/errors")
public class ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ErrorController.class);

    @Post("/report")
    public HttpResponse<?> reportError(@Body Map<String, Object> errorDetails) {
        log.error("Frontend reported error: {}", errorDetails);
        return HttpResponse.ok();
    }
}
