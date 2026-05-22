package net.sixik.ga_profiler;

import java.util.List;

public enum ReportMetricMode {
    CODE_EXECUTION_TIME("executionTime", "Code Execution Time", List.of("Calls", "Min", "Median", "Avg", "P95", "Max", "Total")) {
        @Override
        public ProfileData.MetricStats stats(ProfileData.Snapshot snapshot) {
            return snapshot.getExecutionTime();
        }

        @Override
        public boolean isSupported(ProfileData.Snapshot snapshot) {
            return true;
        }

        @Override
        public String unavailableLabel(ProfileData.Snapshot snapshot) {
            return "";
        }

        @Override
        public String axisLabel() {
            return "Time (" + Profiler.getDisplayUnit().label() + ")";
        }

        @Override
        public double normalize(long rawValue) {
            return Profiler.getDisplayUnit().convertFromNanos(rawValue);
        }

        @Override
        public String format(double value) {
            return String.format(java.util.Locale.US, "%.4f %s", value, Profiler.getDisplayUnit().label());
        }
    },
    CODE_MEMORY_ALLOCATION("memoryAllocation", "Code Memory Allocation", List.of("Calls", "Min Alloc", "Median Alloc", "Avg Alloc", "P95 Alloc", "Max Alloc", "Total Alloc")) {
        @Override
        public ProfileData.MetricStats stats(ProfileData.Snapshot snapshot) {
            return snapshot.getMemoryAllocation();
        }

        @Override
        public boolean isSupported(ProfileData.Snapshot snapshot) {
            return snapshot.getMemoryAllocationAvailability() == ProfileData.MetricAvailability.COLLECTED;
        }

        @Override
        public String unavailableLabel(ProfileData.Snapshot snapshot) {
            if (snapshot.getMemoryAllocationAvailability() == ProfileData.MetricAvailability.NOT_SUPPORTED) {
                return "Not Supported";
            }
            if (snapshot.getMemoryAllocationAvailability() == ProfileData.MetricAvailability.DISABLED) {
                return "Disabled";
            }
            return "";
        }

        @Override
        public String axisLabel() {
            return "Allocated Memory";
        }

        @Override
        public double normalize(long rawValue) {
            return rawValue;
        }

        @Override
        public String format(double value) {
            return formatBytes(value);
        }
    };

    private final String key;
    private final String displayName;
    private final List<String> columnLabels;

    ReportMetricMode(String key, String displayName, List<String> columnLabels) {
        this.key = key;
        this.displayName = displayName;
        this.columnLabels = columnLabels;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> columnLabels() {
        return columnLabels;
    }

    public abstract ProfileData.MetricStats stats(ProfileData.Snapshot snapshot);

    public abstract boolean isSupported(ProfileData.Snapshot snapshot);

    public abstract String unavailableLabel(ProfileData.Snapshot snapshot);

    public abstract String axisLabel();

    public abstract double normalize(long rawValue);

    public abstract String format(double value);

    public double normalizeAvg(ProfileData.Snapshot snapshot) {
        return normalize(Math.round(stats(snapshot).getAvg()));
    }

    private static String formatBytes(double bytes) {
        if (bytes < 1024.0) {
            return String.format(java.util.Locale.US, "%.0f B", bytes);
        }
        if (bytes < 1024.0 * 1024.0) {
            return String.format(java.util.Locale.US, "%.2f KB", bytes / 1024.0);
        }
        if (bytes < 1024.0 * 1024.0 * 1024.0) {
            return String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
