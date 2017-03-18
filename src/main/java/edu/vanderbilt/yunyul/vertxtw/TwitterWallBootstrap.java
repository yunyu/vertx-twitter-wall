package edu.vanderbilt.yunyul.vertxtw;

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

public class TwitterWallBootstrap {
    private static final Logger logger = Logger.getLogger("TwitterWall");
    private static final Vertx vertx = Vertx.vertx();

    public static void main(String[] args) throws IOException {
        HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);

        // Setup routes
        TweetBroadcaster broadcaster = new TweetBroadcaster(router, vertx);
        router.route().handler(StaticHandler.create());
        router.route().failureHandler(ErrorHandler.create());

        logger.info("Loading configuration...");
        Properties properties = new Properties();
        try (InputStream configInput = new FileInputStream(new File("config.properties"))) {
            properties.load(configInput);
        }

        TwitterHandler twitterHandler = new TwitterHandler(properties.getProperty("consumerKey"),
                properties.getProperty("consumerSecret"),
                properties.getProperty("accessToken"),
                properties.getProperty("accessTokenSecret"));
        twitterHandler.setBroadcaster(broadcaster);

        // Deal with circular dependency
        broadcaster.setTwitterHandler(twitterHandler);

        logger.info("Starting webserver...");
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
