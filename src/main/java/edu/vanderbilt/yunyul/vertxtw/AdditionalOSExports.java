package edu.vanderbilt.yunyul.vertxtw;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;

public class AdditionalOSExports extends Collector {
    private static final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = new ArrayList<>();

        mfs.add(new GaugeMetricFamily("os_load_average", "The system load average for the last minute",
                osBean.getSystemLoadAverage()));
        mfs.add(new GaugeMetricFamily("os_avail_processors", "The number of processors available to the Java virtual machine",
                osBean.getAvailableProcessors()));

        return mfs;
    }
}
