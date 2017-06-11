package edu.vanderbilt.yunyul.vertxtw;

import com.codahale.metrics.*;
import com.codahale.metrics.Timer;
import io.prometheus.client.Collector;

import java.util.*;
import java.util.concurrent.TimeUnit;

/*
 * TODO: Move to standalone package
 */
public class DropwizardTimerRateExports extends Collector {
    private final MetricRegistry registry;
    private static double TIMER_FACTOR =  1.0D / TimeUnit.SECONDS.toNanos(1L);

    public DropwizardTimerRateExports(MetricRegistry registry) {
        this.registry = registry;
    }

    private static String getHelpMessage(String metricName, Metric metric) {
        return String.format("Generated from Dropwizard metric import (metric=%s, type=%s)",
                metricName, metric.getClass().getName());
    }

    private List<MetricFamilySamples> fromTimer(String dropwizardName, Timer timer) {
        String name = sanitizeMetricName(dropwizardName);
        Snapshot snapshot = timer.getSnapshot();
        String rateName = name + "_rate";
        List<MetricFamilySamples.Sample> rateSamples = Arrays.asList(
                new MetricFamilySamples.Sample(rateName, Collections.singletonList("interval"), Collections.singletonList("allTime"), timer.getMeanRate()),
                new MetricFamilySamples.Sample(rateName, Collections.singletonList("interval"), Collections.singletonList("oneMinute"), timer.getOneMinuteRate()),
                new MetricFamilySamples.Sample(rateName, Collections.singletonList("interval"), Collections.singletonList("fiveMinute"), timer.getFiveMinuteRate()),
                new MetricFamilySamples.Sample(rateName, Collections.singletonList("interval"), Collections.singletonList("fifteenMinute"), timer.getFifteenMinuteRate())
        );
        String distName = name + "_dist";
        List<MetricFamilySamples.Sample> distSamples = Arrays.asList(
                new MetricFamilySamples.Sample(distName, Collections.singletonList("stat"), Collections.singletonList("mean"), snapshot.getMean() * TIMER_FACTOR),
                new MetricFamilySamples.Sample(distName, Collections.singletonList("stat"), Collections.singletonList("min"), snapshot.getMin() * TIMER_FACTOR),
                new MetricFamilySamples.Sample(distName, Collections.singletonList("stat"), Collections.singletonList("max"), snapshot.getMax() * TIMER_FACTOR),
                new MetricFamilySamples.Sample(distName, Collections.singletonList("stat"), Collections.singletonList("stdDev"), snapshot.getStdDev() * TIMER_FACTOR)
        );
        return Arrays.asList(
                new MetricFamilySamples(rateName, Type.GAUGE, getHelpMessage(dropwizardName, timer), rateSamples),
                new MetricFamilySamples(distName, Type.GAUGE, getHelpMessage(dropwizardName, timer), distSamples)
        );
    }

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = new ArrayList<>();
        for (SortedMap.Entry<String, com.codahale.metrics.Timer> entry : registry.getTimers().entrySet()) {
            String key = entry.getKey();
            mfs.addAll(fromTimer(key, entry.getValue()));
        }
        return mfs;
    }
}
