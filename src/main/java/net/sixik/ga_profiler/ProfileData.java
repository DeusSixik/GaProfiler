package net.sixik.ga_profiler;

import java.io.Serializable;

public class ProfileData implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private String tooltip;
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;
    private long total = 0;
    private int count = 0;

    public ProfileData(String name) {
        this.name = name;
    }

    public synchronized void addSample(long durationNs) {
        if (durationNs < min) min = durationNs;
        if (durationNs > max) max = durationNs;
        total += durationNs;
        count++;
    }

    public String getName() { return name; }
    public String getTooltip() { return tooltip; }
    public void setTooltip(String tooltip) { this.tooltip = tooltip; }
    public long getMin() { return min; }
    public long getMax() { return max; }
    public double getAvg() { return count == 0 ? 0 : (double) total / count; }
    public int getCount() { return count; }
    public long getTotal() { return total; }
}
