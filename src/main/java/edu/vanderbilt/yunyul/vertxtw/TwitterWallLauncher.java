package edu.vanderbilt.yunyul.vertxtw;

import io.vertx.core.Launcher;
import io.vertx.core.VertxOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import io.vertx.ext.dropwizard.MatchType;

public class TwitterWallLauncher extends Launcher {
    public static void main(String[] args) {
        new TwitterWallLauncher().dispatch(args);
    }

    @Override
    public void beforeStartingVertx(VertxOptions options) {
        ((DropwizardMetricsOptions) options.getMetricsOptions())
                .addMonitoredEventBusHandler(new Match().setValue("vertx.circuit-breaker"))
                .addMonitoredEventBusHandler(new Match().setValue("tweet\\..+").setType(MatchType.REGEX));
    }
}
