package edu.vanderbilt.yunyul.vertxtw;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class AdditionalOSExports extends Collector {
    private static final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private static final FileSystem defaultFs = Paths.get(System.getProperty("user.dir")).getFileSystem();

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = new ArrayList<>();

        mfs.add(new GaugeMetricFamily("os_load_average", "The system load average for the last minute",
                osBean.getSystemLoadAverage()));
        mfs.add(new GaugeMetricFamily("os_avail_processors", "The number of processors available to the Java virtual machine",
                osBean.getAvailableProcessors()));

        long availDiskSpace = 0;
        long totalDiskSpace = 0;

        try {
            for (FileStore store : defaultFs.getFileStores()) {
                availDiskSpace += store.getUsableSpace();
                totalDiskSpace += store.getTotalSpace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        mfs.add(new GaugeMetricFamily("disk_space_bytes_used", "The disk space used in bytes for all filesystems",
                totalDiskSpace - availDiskSpace));
        mfs.add(new GaugeMetricFamily("disk_space_bytes_max", "The total disk space in bytes for all filesystems",
                totalDiskSpace));
        
        return mfs;
    }
}
