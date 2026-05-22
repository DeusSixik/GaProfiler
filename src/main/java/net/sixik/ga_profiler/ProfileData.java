package net.sixik.ga_profiler;

public final class ProfileData {
    private ProfileData() {
    }

    public enum MetricAvailability {
        COLLECTED,
        DISABLED,
        NOT_SUPPORTED
    }

    public static final class MetricStats {
        private static final MetricStats EMPTY = new MetricStats(0L, 0L, 0L, 0L);

        private final long min;
        private final long max;
        private final long total;
        private final long count;

        public MetricStats(long min, long max, long total, long count) {
            this.min = min;
            this.max = max;
            this.total = total;
            this.count = count;
        }

        public static MetricStats empty() {
            return EMPTY;
        }

        public long getMin() {
            return min;
        }

        public long getMax() {
            return max;
        }

        public long getTotal() {
            return total;
        }

        public long getCount() {
            return count;
        }

        public double getAvg() {
            return count == 0L ? 0.0 : (double) total / count;
        }
    }

    public static final class Snapshot {
        private final String name;
        private final String tooltip;
        private final MetricStats executionTime;
        private final MetricStats memoryAllocation;
        private final MetricAvailability memoryAllocationAvailability;

        public Snapshot(
                String name,
                String tooltip,
                MetricStats executionTime,
                MetricStats memoryAllocation,
                MetricAvailability memoryAllocationAvailability
        ) {
            this.name = name;
            this.tooltip = tooltip;
            this.executionTime = executionTime;
            this.memoryAllocation = memoryAllocation;
            this.memoryAllocationAvailability = memoryAllocationAvailability;
        }

        public String getName() {
            return name;
        }

        public String getTooltip() {
            return tooltip;
        }

        public MetricStats getExecutionTime() {
            return executionTime;
        }

        public MetricStats getMemoryAllocation() {
            return memoryAllocation;
        }

        public MetricAvailability getMemoryAllocationAvailability() {
            return memoryAllocationAvailability;
        }

        // Backward-compatible execution-time accessors.
        public long getMin() {
            return executionTime.getMin();
        }

        public long getMax() {
            return executionTime.getMax();
        }

        public long getTotal() {
            return executionTime.getTotal();
        }

        public long getCount() {
            return executionTime.getCount();
        }

        public double getAvg() {
            return executionTime.getAvg();
        }
    }
}
