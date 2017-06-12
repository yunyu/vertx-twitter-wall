package edu.vanderbilt.yunyul.vertxtw;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.slf4j.LoggerFactory;


public class LoggingHandler {
    private final EventBus eventBus;
    private static final String PREFIX = "vertx.console.logger.";

    public LoggingHandler(Router router, Vertx vertx) {
        this.eventBus = vertx.eventBus();

        // Initialize bridge components
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);

        // Allow tweet broadcasts
        PermittedOptions tweetPermitted = new PermittedOptions().setAddressRegex("vertx\\.console\\.logger\\..+");
        BridgeOptions options = new BridgeOptions()
                // No incoming messages permitted
                .addOutboundPermitted(tweetPermitted);

        sockJSHandler.bridge(options);

        router.route("/loggerproxy/*").handler(sockJSHandler);

        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = lc.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

        Appender<ILoggingEvent> eventBusAppender = new EventBusAppender();
        eventBusAppender.setContext(lc);
        eventBusAppender.start();
        rootLogger.addAppender(eventBusAppender);
    }

    public class EventBusAppender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            JsonObject eventJson = new JsonObject();
            eventJson.put("date", event.getTimeStamp());
            eventJson.put("level", event.getLevel().toString());
            eventJson.put("message", event.getMessage());
            eventJson.put("thread", event.getThreadName());

            eventBus.publish(PREFIX + event.getLoggerContextVO().getName(), eventJson.toString());
        }
    }
}
