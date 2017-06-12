package edu.vanderbilt.yunyul.vertxtw;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import in.yunyul.prometheus.extras.AdditionalJVMExports;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.vertx.MetricsHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.http.HttpServer;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.rest.ServiceDiscoveryRestEndpoint;
import io.vertx.servicediscovery.types.EventBusService;
import io.vertx.servicediscovery.types.HttpEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwitterWallVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(TwitterWallVerticle.class);

    @Override
    public void start() throws Exception {
        HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);

        int port = config().getInteger("port", 8080);

        vertx.deployVerticle(new DummyWorkerVerticle(), new DeploymentOptions().setWorker(true));

        router.route("/metrics").handler(CorsHandler.create("*"));

        MetricRegistry dropwizardRegistry = SharedMetricRegistries.getOrCreate(
                System.getProperty("vertx.metrics.options.registryName")
        );
        CollectorRegistry defaultRegistry = CollectorRegistry.defaultRegistry;
        defaultRegistry.register(new DropwizardExports(dropwizardRegistry));
        DefaultExports.initialize();
        new AdditionalJVMExports().register();
        new DropwizardTimerRateExports(dropwizardRegistry).register();
        new LoggingHandler(router, vertx);

        ServiceDiscovery discovery = ServiceDiscovery.create(vertx,
                new ServiceDiscoveryOptions()
                        .setName("twitter-wall"));
        ServiceDiscoveryRestEndpoint.create(router, discovery);

        Record metricsRecord = HttpEndpoint.createRecord("prometheus-metrics", "localhost", port, "/metrics");
        discovery.publish(metricsRecord, ar -> {
            if (ar.succeeded()) {
                log("Successfully published metrics record");
            }
        });

        router.route("/metrics").handler(new MetricsHandler());

        router.route("/admin").handler(StaticHandler.create("adminfiles"));
        router.route("/dist/*").handler(StaticHandler.create("adminfiles/dist"));

        TweetBroadcaster broadcaster = new TweetBroadcaster(router, vertx);
        router.route("/*").handler(StaticHandler.create());
        router.route("/*").failureHandler(ErrorHandler.create());

        TwitterStreamHandler twitterStreamHandler = new TwitterStreamHandler(config());
        Record twitterRecord = EventBusService.createRecord(
                "twitter-broadcast",
                "tweet.main",
                TweetBroadcaster.class
        );
        discovery.publish(twitterRecord, ar -> {
            if (ar.succeeded()) {
                log("Successfully published Twitter record");
            }
        });

        // Register circular dependency
        twitterStreamHandler.setBroadcaster(broadcaster);
        broadcaster.setTwitterStreamHandler(twitterStreamHandler);

        log("Starting webserver...");
        httpServer.requestHandler(router::accept).listen(port);

    }

    /**
     * Logs a message to the global logger
     * @param msg The message to log
     */
    public static void log(String msg) {
        logger.info(msg);
    }
}
