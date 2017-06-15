package edu.vanderbilt.yunyul.vertxtw;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class LoggingHandler {
    private final EventBus eventBus;
    private static final String PREFIX = "vertx.console.logger.";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String ROOT_LOGGER_NAME = ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME;

    public LoggingHandler(Router router, Vertx vertx) {
        this.eventBus = vertx.eventBus();

        // Set up streaming
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        // Allow log broadcasts
        PermittedOptions tweetPermitted = new PermittedOptions().setAddressRegex("vertx\\.console\\.logger\\..+");
        BridgeOptions options = new BridgeOptions()
                // No incoming messages permitted
                .addOutboundPermitted(tweetPermitted);
        sockJSHandler.bridge(options);
        router.route("/loggerproxy/*").handler(sockJSHandler);

        // Set up appender
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Appender<ILoggingEvent> eventBusAppender = new EventBusAppender();
        eventBusAppender.setContext(lc);
        eventBusAppender.start();
        lc.getLogger(ROOT_LOGGER_NAME).addAppender(eventBusAppender);

        // Set up routes
        router.route("/loggers*").handler(BodyHandler.create());

        router.route(HttpMethod.POST, "/loggers/:logger/setlevel").produces(JSON_CONTENT_TYPE)

        router.route(HttpMethod.GET, "/loggers").produces(JSON_CONTENT_TYPE).handler(ctx -> {
            JsonArray loggers = new JsonArray();
            for (ch.qos.logback.classic.Logger log : lc.getLoggerList()) {
                loggers.add(getLoggerInfo(log));
            }
            ctx.response().putHeader("content-type", JSON_CONTENT_TYPE).end(loggers.toString());
        });
    }


    private static JsonObject getLoggerInfo(ch.qos.logback.classic.Logger logger) {
        JsonObject loggerInfo = new JsonObject();
        loggerInfo.put("name", logger.getName());
        loggerInfo.put("effectiveLevel", logger.getEffectiveLevel().toString());
        return loggerInfo;
    }

    public class EventBusAppender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            JsonObject eventJson = new JsonObject();
            eventJson.put("date", event.getTimeStamp());
            eventJson.put("level", event.getLevel().toString());
            eventJson.put("message", event.getMessage());
            eventJson.put("thread", event.getThreadName());
            eventJson.put("logger", event.getLoggerName());

            eventBus.publish(PREFIX + event.getLoggerContextVO().getName(), eventJson.toString());
        }
    }
}
