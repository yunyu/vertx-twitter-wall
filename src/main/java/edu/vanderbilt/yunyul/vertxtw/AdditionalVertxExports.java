package edu.vanderbilt.yunyul.vertxtw;

import io.prometheus.client.Collector;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.dropwizard.MetricsService;

import java.util.Collections;
import java.util.List;

import static edu.vanderbilt.yunyul.vertxtw.TwitterWallVerticle.log;

public class AdditionalVertxExports extends Collector {
    private final MetricsService metricsService;
    private final HttpServer httpServer;

    public AdditionalVertxExports(Vertx vertx, HttpServer httpServer) {
        metricsService = MetricsService.create(vertx);
        this.httpServer = httpServer;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        JsonObject httpSnapshot = metricsService.getMetricsSnapshot(httpServer);
        if (httpSnapshot != null) {
            log(httpSnapshot.toString());
        } else {
            log("httpSnapshot is null");
        }
        return Collections.emptyList();
    }
}
