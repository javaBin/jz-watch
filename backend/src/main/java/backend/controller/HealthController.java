package backend.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.Map;

@Controller("/health")
public class HealthController {

    @Get(produces = MediaType.APPLICATION_JSON)
    public HttpResponse<?> health() {
        return HttpResponse.ok(Map.of("status", "UP"));
    }
}
