package edu.vanderbilt.yunyul.vertxtw;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.io.*;
import java.util.Properties;
import java.util.logging.Logger;

public class TwitterWallBootstrap {
    private static final Logger logger = Logger.getLogger("TwitterWall");
    private static final Vertx vertx = Vertx.vertx();

    public static void log(String msg) {
        logger.info(msg);
    }

    public static void main(String[] args) throws IOException {
        HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);
        TweetBroadcaster broadcaster = new TweetBroadcaster(router, vertx);
        router.route().handler(StaticHandler.create());
        router.route().failureHandler(ErrorHandler.create());

        logger.info("Loading configuration...");
        try (InputStream configInput = new FileInputStream(new File("config.properties"))) {
            Properties properties = new Properties();
            properties.load(configInput);

            TwitterHandler twitterHandler = new TwitterHandler(properties.getProperty("consumerKey"),
                    properties.getProperty("consumerSecret"),
                    properties.getProperty("accessToken"),
                    properties.getProperty("accessTokenSecret"));
            twitterHandler.setBroadcaster(broadcaster);
            broadcaster.setTwitterHandler(twitterHandler);
        }

        httpServer.requestHandler(router::accept).listen(8080);
    }
}
