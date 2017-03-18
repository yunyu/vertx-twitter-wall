package edu.vanderbilt.yunyul.vertxtw;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

public class TwitterWallVerticle extends AbstractVerticle {
    private static final Logger logger = Logger.getLogger("TwitterWall");

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new TwitterWallVerticle());
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);

        // Setup routes
        TweetBroadcaster broadcaster = new TweetBroadcaster(router, vertx);
        router.route().handler(StaticHandler.create());
        router.route().failureHandler(ErrorHandler.create());

        log("Loading configuration...");
        Properties properties = new Properties();
        try (InputStream configInput = new FileInputStream(new File("config.properties"))) {
            properties.load(configInput);
        }

        TwitterStreamHandler twitterHandler = new TwitterStreamHandler(properties.getProperty("consumerKey"),
                properties.getProperty("consumerSecret"),
                properties.getProperty("accessToken"),
                properties.getProperty("accessTokenSecret"));
        twitterHandler.setBroadcaster(broadcaster);

        // Deal with circular dependency
        broadcaster.setTwitterHandler(twitterHandler);

        log("Starting webserver...");
        httpServer.requestHandler(router::accept).listen(Integer.parseInt(properties.getProperty("port")));
    }

    /**
     * Logs a message to the global logger
     * @param msg The message to log
     */
    public static void log(String msg) {
        logger.info(msg);
    }
}
