package edu.vanderbilt.yunyul.vertxtw;

import com.codahale.metrics.SharedMetricRegistries;
import in.yunyul.prometheus.extras.AdditionalJVMExports;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.vertx.MetricsHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.dropwizard.MetricsService;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.StaticHandler;

public class TwitterWallVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(TwitterWallVerticle.class);

    @Override
    public void start() throws Exception {
        HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);

        vertx.deployVerticle(new DummyWorkerVerticle(), new DeploymentOptions().setWorker(true));

        router.route("/metrics").handler(CorsHandler.create("*"));

        CollectorRegistry defaultRegistry = CollectorRegistry.defaultRegistry;
        defaultRegistry.register(new DropwizardExports(SharedMetricRegistries.getOrCreate(
                System.getProperty("vertx.metrics.options.registryName")
        )));
        DefaultExports.initialize();
        new AdditionalJVMExports().register();
        new AdditionalVertxExports(vertx, httpServer).register();

        router.route("/metrics").handler(new MetricsHandler());

        router.route("/admin").handler(StaticHandler.create("adminfiles"));
        router.route("/dist/*").handler(StaticHandler.create("adminfiles/dist"));

        TweetBroadcaster broadcaster = new TweetBroadcaster(router, vertx);
        router.route("/*").handler(StaticHandler.create());
        router.route("/*").failureHandler(ErrorHandler.create());

        TwitterStreamHandler twitterStreamHandler = new TwitterStreamHandler(config());

        // Register circular dependency
        twitterStreamHandler.setBroadcaster(broadcaster);
        broadcaster.setTwitterStreamHandler(twitterStreamHandler);

        log("Starting webserver...");
        httpServer.requestHandler(router::accept).listen(config().getInteger("port", 8080));

        // MetricsService metricsService = MetricsService.create(vertx);
        // log(metricsService.getMetricsSnapshot(httpServer).toString());

    }

    /**
     * Logs a message to the global logger
     * @param msg The message to log
     */
    public static void log(String msg) {
        logger.info(msg);
    }
}
