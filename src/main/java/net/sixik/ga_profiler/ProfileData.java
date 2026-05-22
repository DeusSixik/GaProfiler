package net.sixik.ga_profiler;

public final class ProfileData {
    private ProfileData() {
    }

    public static final class Snapshot {
        private final String name;
        private final String tooltip;
        private final long min;
        private final long max;
        private final long total;
        private final long count;

        public Snapshot(String name, String tooltip, long min, long max, long total, long count) {
            this.name = name;
            this.tooltip = tooltip;
            this.min = min;
            this.max = max;
            this.total = total;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public String getTooltip() {
            return tooltip;
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
}
