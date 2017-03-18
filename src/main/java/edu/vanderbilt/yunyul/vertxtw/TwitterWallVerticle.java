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
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

public class TwitterWallVerticle extends AbstractVerticle {
    private static final Logger logger = Logger.getLogger("TwitterWall");

    @Override
    public void start() throws Exception {
        HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);

        // Setup routes
        TweetBroadcaster broadcaster = new TweetBroadcaster(router, vertx);
        router.route().handler(StaticHandler.create());
        router.route().failureHandler(ErrorHandler.create());

        TwitterStreamHandler twitterStreamHandler = new TwitterStreamHandler(
                config().getString("consumerKey"),
                config().getString("consumerSecret"),
                config().getString("accessToken"),
                config().getString("accessTokenSecret"));

        // Register circular dependency
        twitterStreamHandler.setBroadcaster(broadcaster);
        broadcaster.setTwitterStreamHandler(twitterStreamHandler);

        log("Starting webserver...");
        httpServer.requestHandler(router::accept).listen(config().getInteger("port", 8080));
    }

    /**
     * Logs a message to the global logger
     * @param msg The message to log
     */
    public static void log(String msg) {
        logger.info(msg);
    }
}
