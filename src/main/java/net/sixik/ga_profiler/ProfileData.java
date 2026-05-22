package net.sixik.ga_profiler;

import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

public class ProfileData {
    private final String name;
    private String tooltip;

    private final LongAccumulator min = new LongAccumulator(Math::min, Long.MAX_VALUE);
    private final LongAccumulator max = new LongAccumulator(Math::max, Long.MIN_VALUE);
    private final LongAdder total = new LongAdder();
    private final LongAdder count = new LongAdder();

    public ProfileData(String name) {
        this.name = name;
    }

    public void addSample(long durationNs) {
        min.accumulate(durationNs);
        max.accumulate(durationNs);
        total.add(durationNs);
        count.increment();
    }

    public String getName() {
        return name;
    }

    public String getTooltip() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }

    public Snapshot takeSnapshot() {
        return new Snapshot(
                name,
                tooltip,
                min.get() == Long.MAX_VALUE ? 0 : min.get(),
                max.get() == Long.MIN_VALUE ? 0 : max.get(),
                total.sum(),
                count.intValue()
        );
    }

    public static class Snapshot {
        public String name;
        public String tooltip;
        public long min;
        public long max;
        public long total;
        public int count;

        public Snapshot(String name, String tooltip, long min, long max, long total, int count) {
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

        public int getCount() {
            return count;
        }

        public double getAvg() {
            return count == 0 ? 0 : (double) total / count;
        }
    }
}
