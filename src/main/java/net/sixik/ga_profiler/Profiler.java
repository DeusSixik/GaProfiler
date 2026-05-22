package net.sixik.ga_profiler;

import com.sun.management.ThreadMXBean;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class Profiler {
    private static final int INITIAL_STACK_CAPACITY = 256;
    private static final int INITIAL_SECTION_CAPACITY = 16;
    private static final String DUMP_FORMAT_V2 = "GA_PROFILER_DUMP_V2";
    private static final String DUMP_FORMAT_V3 = "GA_PROFILER_DUMP_V3";

    private static final List<Section> SECTIONS = new ArrayList<>();
    private static final Map<String, Section> SECTIONS_BY_NAME = new HashMap<>();
    private static final CopyOnWriteArrayList<ThreadState> LIVE_STATES = new CopyOnWriteArrayList<>();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ThreadLocal<ThreadState> STATE = ThreadLocal.withInitial(Profiler::createThreadState);
    private static final AllocationCounter JVM_ALLOCATION_COUNTER = new JvmAllocationCounter();

    private static volatile AllocationCounter allocationCounter = JVM_ALLOCATION_COUNTER;
    private static volatile boolean allocationProfilingEnabled;
    private static volatile long defaultSampleLimit = 0L;
    private static volatile TimeUnit displayUnit = TimeUnit.MILLISECONDS;

    private Profiler() {
    }

    public enum TimeUnit {
        NANOSECONDS("ns", 1.0),
        MILLISECONDS("ms", 1_000_000.0),
        SECONDS("s", 1_000_000_000.0);

        private final String label;
        private final double divisor;

        TimeUnit(String label, double divisor) {
            this.label = label;
            this.divisor = divisor;
        }

        public String label() {
            return label;
        }

        public double convertFromNanos(long nanos) {
            return nanos / divisor;
        }
    }

    public interface AllocationCounter {
        boolean isSupported();

        long currentThreadAllocatedBytes();
    }

    public static final class Section {
        private final int id;
        private final String name;
        private final String tooltip;
        private volatile long maxSamples;

        private Section(int id, String name, String tooltip, long maxSamples) {
            this.id = id;
            this.name = name;
            this.tooltip = tooltip;
            this.maxSamples = maxSamples;
        }

        public int id() {
            return id;
        }

        public String name() {
            return name;
        }

        public String tooltip() {
            return tooltip;
        }

        public long maxSamples() {
            return maxSamples;
        }
    }

    private static final class ThreadState {
        private long generation;
        private int depth;
        private int[] sectionStack = new int[INITIAL_STACK_CAPACITY];
        private long[] startTimes = new long[INITIAL_STACK_CAPACITY];
        private long[] allocationStartBytes = new long[INITIAL_STACK_CAPACITY];
        private long[] minDurations = new long[INITIAL_SECTION_CAPACITY];
        private long[] maxDurations = new long[INITIAL_SECTION_CAPACITY];
        private long[] totalDurations = new long[INITIAL_SECTION_CAPACITY];
        private long[] counts = new long[INITIAL_SECTION_CAPACITY];
        private long[][] durationSamples = new long[INITIAL_SECTION_CAPACITY][];
        private int[] durationSampleSizes = new int[INITIAL_SECTION_CAPACITY];
        private long[] minAllocatedBytes = new long[INITIAL_SECTION_CAPACITY];
        private long[] maxAllocatedBytes = new long[INITIAL_SECTION_CAPACITY];
        private long[] totalAllocatedBytes = new long[INITIAL_SECTION_CAPACITY];
        private long[] allocationCounts = new long[INITIAL_SECTION_CAPACITY];
        private long[][] allocationSamples = new long[INITIAL_SECTION_CAPACITY][];
        private int[] allocationSampleSizes = new int[INITIAL_SECTION_CAPACITY];
        private boolean[] touched = new boolean[INITIAL_SECTION_CAPACITY];
        private int[] touchedIds = new int[INITIAL_SECTION_CAPACITY];
        private int touchedCount;
    }

    private static final class JvmAllocationCounter implements AllocationCounter {
        private final ThreadMXBean bean;
        private final boolean supported;

        private JvmAllocationCounter() {
            java.lang.management.ThreadMXBean rawBean = ManagementFactory.getThreadMXBean();
            if (rawBean instanceof ThreadMXBean threadBean && threadBean.isThreadAllocatedMemorySupported()) {
                if (!threadBean.isThreadAllocatedMemoryEnabled()) {
                    try {
                        threadBean.setThreadAllocatedMemoryEnabled(true);
                    } catch (SecurityException | UnsupportedOperationException ignored) {
                        // Fall back to unsupported mode if the JVM refuses to enable counters.
                    }
                }
                this.bean = threadBean;
                this.supported = threadBean.isThreadAllocatedMemoryEnabled();
            } else {
                this.bean = null;
                this.supported = false;
            }
        }

        @Override
        public boolean isSupported() {
            return supported;
        }

        @Override
        public long currentThreadAllocatedBytes() {
            if (!supported) {
                return -1L;
            }
            return Math.max(0L, bean.getThreadAllocatedBytes(Thread.currentThread().getId()));
        }
    }

    public interface ProfileScope extends AutoCloseable {
        @Override
        void close();
    }

    public static synchronized Section register(String name, String tooltip, long maxSamples) {
        Objects.requireNonNull(name, "name");
        validateLimit(maxSamples);

        if (SECTIONS_BY_NAME.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate section: " + name);
        }

        long effectiveLimit = maxSamples <= 0 ? defaultSampleLimit : maxSamples;
        Section section = new Section(SECTIONS.size(), name, tooltip, effectiveLimit);
        SECTIONS.add(section);
        SECTIONS_BY_NAME.put(name, section);
        return section;
    }

    public static void push(Section section) {
        Objects.requireNonNull(section, "section");
        ThreadState state = state();
        ensureStackCapacity(state, state.depth + 1);
        state.sectionStack[state.depth] = section.id;
        state.startTimes[state.depth] = System.nanoTime();
        state.allocationStartBytes[state.depth] = allocationProfilingEnabled && allocationCounter.isSupported()
                ? allocationCounter.currentThreadAllocatedBytes()
                : -1L;
        state.depth++;
    }

    public static void pop() {
        ThreadState state = state();
        if (state.depth == 0) {
            throw new IllegalStateException("Profiler.pop() without matching push()");
        }

        int stackIndex = --state.depth;
        int sectionId = state.sectionStack[stackIndex];
        long duration = System.nanoTime() - state.startTimes[stackIndex];
        Section section = sectionById(sectionId);

        ensureAggregateCapacity(state, sectionId + 1);
        long currentCount = state.counts[sectionId];
        if (section.maxSamples > 0 && currentCount >= section.maxSamples) {
            return;
        }

        recordExecutionDuration(state, section, duration);

        long allocationStart = state.allocationStartBytes[stackIndex];
        if (allocationProfilingEnabled && allocationStart >= 0L) {
            long allocationDelta = Math.max(0L, allocationCounter.currentThreadAllocatedBytes() - allocationStart);
            recordAllocationBytes(state, section, allocationDelta);
        }
    }

    public static void addSample(Section section, long durationNs) {
        Objects.requireNonNull(section, "section");
        if (durationNs < 0) {
            throw new IllegalArgumentException("durationNs must be >= 0");
        }

        ThreadState state = state();
        ensureAggregateCapacity(state, section.id + 1);
        long currentCount = state.counts[section.id];
        if (section.maxSamples > 0 && currentCount >= section.maxSamples) {
            return;
        }

        recordExecutionDuration(state, section, durationNs);
    }

    public static ProfileScope scope(Section section) {
        push(section);
        return Profiler::pop;
    }

    public static synchronized void configureDefaultSampleLimit(long maxSamples) {
        validateLimit(maxSamples);
        defaultSampleLimit = maxSamples;
    }

    public static synchronized void applySampleLimitToAllSections(long maxSamples) {
        validateLimit(maxSamples);
        defaultSampleLimit = maxSamples;
        for (Section section : SECTIONS) {
            section.maxSamples = maxSamples;
        }
    }

    public static void setDisplayUnit(TimeUnit unit) {
        displayUnit = Objects.requireNonNull(unit, "unit");
    }

    public static TimeUnit getDisplayUnit() {
        return displayUnit;
    }

    public static void setAllocationProfilingEnabled(boolean enabled) {
        allocationProfilingEnabled = enabled;
    }

    public static boolean isAllocationProfilingEnabled() {
        return allocationProfilingEnabled;
    }

    static void useAllocationCounterForTesting(AllocationCounter counter) {
        allocationCounter = Objects.requireNonNull(counter, "counter");
    }

    static void clearAllocationCounterOverrideForTesting() {
        allocationCounter = JVM_ALLOCATION_COUNTER;
    }

    public static void reset() {
        long generation = GENERATION.incrementAndGet();
        clearThreadState(STATE.get(), generation);
    }

    public static Collection<ProfileData.Snapshot> getData() {
        List<Section> sections;
        synchronized (Profiler.class) {
            sections = new ArrayList<>(SECTIONS);
        }

        long generation = GENERATION.get();
        List<ProfileData.Snapshot> snapshots = new ArrayList<>();
        ProfileData.MetricAvailability allocationAvailability = allocationAvailability();

        for (Section section : sections) {
            ProfileData.MetricStats executionStats = mergeExecutionStats(section.id, generation);
            if (executionStats.getCount() == 0L) {
                continue;
            }

            ProfileData.MetricStats allocationStats = allocationAvailability == ProfileData.MetricAvailability.COLLECTED
                    ? mergeAllocationStats(section.id, generation)
                    : ProfileData.MetricStats.empty();
            ProfileData.MetricAvailability snapshotAvailability = allocationAvailability;
            if (snapshotAvailability == ProfileData.MetricAvailability.COLLECTED && allocationStats.getCount() == 0L) {
                snapshotAvailability = ProfileData.MetricAvailability.DISABLED;
            }

            snapshots.add(new ProfileData.Snapshot(
                    section.name,
                    section.tooltip,
                    executionStats,
                    allocationStats,
                    snapshotAvailability
            ));
        }

        return snapshots;
    }

    public static void dump(String path) {
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(path))) {
            writer.write(DUMP_FORMAT_V3);
            writer.write('\n');
            writer.write("Name\tTooltip\tTimeMin\tTimeMedian\tTimeMax\tTimeP95\tTimeTotal\tTimeCount\tAllocMin\tAllocMedian\tAllocMax\tAllocP95\tAllocTotal\tAllocCount\tAllocAvailability\n");

            for (ProfileData.Snapshot snapshot : getData()) {
                writer.write(String.join("\t",
                        sanitizeField(snapshot.getName()),
                        sanitizeField(snapshot.getTooltip()),
                        Long.toString(snapshot.getExecutionTime().getMin()),
                        Long.toString(snapshot.getExecutionTime().getMedian()),
                        Long.toString(snapshot.getExecutionTime().getMax()),
                        Long.toString(snapshot.getExecutionTime().getP95()),
                        Long.toString(snapshot.getExecutionTime().getTotal()),
                        Long.toString(snapshot.getExecutionTime().getCount()),
                        Long.toString(snapshot.getMemoryAllocation().getMin()),
                        Long.toString(snapshot.getMemoryAllocation().getMedian()),
                        Long.toString(snapshot.getMemoryAllocation().getMax()),
                        Long.toString(snapshot.getMemoryAllocation().getP95()),
                        Long.toString(snapshot.getMemoryAllocation().getTotal()),
                        Long.toString(snapshot.getMemoryAllocation().getCount()),
                        snapshot.getMemoryAllocationAvailability().name()
                ));
                writer.write('\n');
            }
        } catch (IOException e) {
            System.err.println("Failed to dump profile data: " + e.getMessage());
        }
    }

    public static Collection<ProfileData.Snapshot> load(String path) {
        List<ProfileData.Snapshot> snapshots = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(Path.of(path))) {
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return snapshots;
            }

            if (DUMP_FORMAT_V3.equals(firstLine)) {
                loadV3(reader, snapshots);
            } else if (DUMP_FORMAT_V2.equals(firstLine)) {
                loadV2(reader, snapshots);
            } else {
                loadLegacyV1(reader, snapshots);
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Failed to load profile data: " + e.getMessage());
        }

        return snapshots;
    }

    private static ThreadState createThreadState() {
        ThreadState state = new ThreadState();
        state.generation = GENERATION.get();
        LIVE_STATES.add(state);
        return state;
    }

    private static ThreadState state() {
        ThreadState state = STATE.get();
        long generation = GENERATION.get();
        if (state.generation != generation) {
            clearThreadState(state, generation);
        }
        return state;
    }

    private static synchronized Section sectionById(int sectionId) {
        return SECTIONS.get(sectionId);
    }

    private static void validateLimit(long maxSamples) {
        if (maxSamples < 0L) {
            throw new IllegalArgumentException("maxSamples must be >= 0");
        }
    }

    private static void ensureStackCapacity(ThreadState state, int requiredCapacity) {
        if (requiredCapacity <= state.sectionStack.length) {
            return;
        }

        int newCapacity = state.sectionStack.length;
        while (newCapacity < requiredCapacity) {
            newCapacity *= 2;
        }

        state.sectionStack = java.util.Arrays.copyOf(state.sectionStack, newCapacity);
        state.startTimes = java.util.Arrays.copyOf(state.startTimes, newCapacity);
        state.allocationStartBytes = java.util.Arrays.copyOf(state.allocationStartBytes, newCapacity);
    }

    private static void ensureAggregateCapacity(ThreadState state, int requiredCapacity) {
        if (requiredCapacity <= state.counts.length) {
            return;
        }

        int newCapacity = state.counts.length;
        while (newCapacity < requiredCapacity) {
            newCapacity *= 2;
        }

        state.minDurations = java.util.Arrays.copyOf(state.minDurations, newCapacity);
        state.maxDurations = java.util.Arrays.copyOf(state.maxDurations, newCapacity);
        state.totalDurations = java.util.Arrays.copyOf(state.totalDurations, newCapacity);
        state.counts = java.util.Arrays.copyOf(state.counts, newCapacity);
        state.durationSamples = java.util.Arrays.copyOf(state.durationSamples, newCapacity);
        state.durationSampleSizes = java.util.Arrays.copyOf(state.durationSampleSizes, newCapacity);
        state.minAllocatedBytes = java.util.Arrays.copyOf(state.minAllocatedBytes, newCapacity);
        state.maxAllocatedBytes = java.util.Arrays.copyOf(state.maxAllocatedBytes, newCapacity);
        state.totalAllocatedBytes = java.util.Arrays.copyOf(state.totalAllocatedBytes, newCapacity);
        state.allocationCounts = java.util.Arrays.copyOf(state.allocationCounts, newCapacity);
        state.allocationSamples = java.util.Arrays.copyOf(state.allocationSamples, newCapacity);
        state.allocationSampleSizes = java.util.Arrays.copyOf(state.allocationSampleSizes, newCapacity);
        state.touched = java.util.Arrays.copyOf(state.touched, newCapacity);
        state.touchedIds = java.util.Arrays.copyOf(state.touchedIds, newCapacity);
    }

    private static void recordExecutionDuration(ThreadState state, Section section, long duration) {
        int sectionId = section.id;
        if (!state.touched[sectionId]) {
            state.touched[sectionId] = true;
            if (state.touchedCount == state.touchedIds.length) {
                state.touchedIds = java.util.Arrays.copyOf(state.touchedIds, state.touchedIds.length * 2);
            }
            state.touchedIds[state.touchedCount++] = sectionId;
            state.minDurations[sectionId] = duration;
            state.maxDurations[sectionId] = duration;
        } else {
            state.minDurations[sectionId] = Math.min(state.minDurations[sectionId], duration);
            state.maxDurations[sectionId] = Math.max(state.maxDurations[sectionId], duration);
        }

        state.totalDurations[sectionId] += duration;
        state.counts[sectionId]++;
        appendSample(state.durationSamples, state.durationSampleSizes, sectionId, duration, section.maxSamples);
    }

    private static void recordAllocationBytes(ThreadState state, Section section, long bytes) {
        int sectionId = section.id;
        if (state.allocationCounts[sectionId] == 0L) {
            state.minAllocatedBytes[sectionId] = bytes;
            state.maxAllocatedBytes[sectionId] = bytes;
        } else {
            state.minAllocatedBytes[sectionId] = Math.min(state.minAllocatedBytes[sectionId], bytes);
            state.maxAllocatedBytes[sectionId] = Math.max(state.maxAllocatedBytes[sectionId], bytes);
        }

        state.totalAllocatedBytes[sectionId] += bytes;
        state.allocationCounts[sectionId]++;
        appendSample(state.allocationSamples, state.allocationSampleSizes, sectionId, bytes, section.maxSamples);
    }

    private static ProfileData.MetricStats mergeExecutionStats(int sectionId, long generation) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long total = 0L;
        long count = 0L;
        int sampleCount = 0;

        for (ThreadState state : LIVE_STATES) {
            if (state.generation != generation || sectionId >= state.counts.length) {
                continue;
            }

            long stateCount = state.counts[sectionId];
            if (stateCount == 0L) {
                continue;
            }

            count += stateCount;
            total += state.totalDurations[sectionId];
            min = Math.min(min, state.minDurations[sectionId]);
            max = Math.max(max, state.maxDurations[sectionId]);
            sampleCount += state.durationSampleSizes[sectionId];
        }

        if (count == 0L) {
            return ProfileData.MetricStats.empty();
        }

        long[] sortedSamples = mergedSortedSamples(generation, sectionId, true, sampleCount);

        return new ProfileData.MetricStats(
                min == Long.MAX_VALUE ? 0L : min,
                medianOf(sortedSamples),
                max == Long.MIN_VALUE ? 0L : max,
                percentileOf(sortedSamples, 0.95),
                total,
                count
        );
    }

    private static ProfileData.MetricStats mergeAllocationStats(int sectionId, long generation) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long total = 0L;
        long count = 0L;
        int sampleCount = 0;

        for (ThreadState state : LIVE_STATES) {
            if (state.generation != generation || sectionId >= state.allocationCounts.length) {
                continue;
            }

            long stateCount = state.allocationCounts[sectionId];
            if (stateCount == 0L) {
                continue;
            }

            count += stateCount;
            total += state.totalAllocatedBytes[sectionId];
            min = Math.min(min, state.minAllocatedBytes[sectionId]);
            max = Math.max(max, state.maxAllocatedBytes[sectionId]);
            sampleCount += state.allocationSampleSizes[sectionId];
        }

        if (count == 0L) {
            return ProfileData.MetricStats.empty();
        }

        long[] sortedSamples = mergedSortedSamples(generation, sectionId, false, sampleCount);

        return new ProfileData.MetricStats(
                min == Long.MAX_VALUE ? 0L : min,
                medianOf(sortedSamples),
                max == Long.MIN_VALUE ? 0L : max,
                percentileOf(sortedSamples, 0.95),
                total,
                count
        );
    }

    private static ProfileData.MetricAvailability allocationAvailability() {
        if (!allocationProfilingEnabled) {
            return ProfileData.MetricAvailability.DISABLED;
        }
        return allocationCounter.isSupported()
                ? ProfileData.MetricAvailability.COLLECTED
                : ProfileData.MetricAvailability.NOT_SUPPORTED;
    }

    private static void clearThreadState(ThreadState state, long generation) {
        for (int i = 0; i < state.touchedCount; i++) {
            int sectionId = state.touchedIds[i];
            state.minDurations[sectionId] = 0L;
            state.maxDurations[sectionId] = 0L;
            state.totalDurations[sectionId] = 0L;
            state.counts[sectionId] = 0L;
            state.durationSampleSizes[sectionId] = 0;
            state.minAllocatedBytes[sectionId] = 0L;
            state.maxAllocatedBytes[sectionId] = 0L;
            state.totalAllocatedBytes[sectionId] = 0L;
            state.allocationCounts[sectionId] = 0L;
            state.allocationSampleSizes[sectionId] = 0;
            state.touched[sectionId] = false;
            state.touchedIds[i] = 0;
        }

        state.depth = 0;
        state.touchedCount = 0;
        state.generation = generation;
    }

    private static void loadV3(BufferedReader reader, List<ProfileData.Snapshot> snapshots) throws IOException {
        reader.readLine();

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] parts = line.split("\t", -1);
            if (parts.length < 15) {
                continue;
            }

            snapshots.add(new ProfileData.Snapshot(
                    parts[0],
                    parts[1].isEmpty() ? null : parts[1],
                    new ProfileData.MetricStats(
                            Long.parseLong(parts[2]),
                            Long.parseLong(parts[3]),
                            Long.parseLong(parts[4]),
                            Long.parseLong(parts[5]),
                            Long.parseLong(parts[6]),
                            Long.parseLong(parts[7])
                    ),
                    new ProfileData.MetricStats(
                            Long.parseLong(parts[8]),
                            Long.parseLong(parts[9]),
                            Long.parseLong(parts[10]),
                            Long.parseLong(parts[11]),
                            Long.parseLong(parts[12]),
                            Long.parseLong(parts[13])
                    ),
                    ProfileData.MetricAvailability.valueOf(parts[14])
            ));
        }
    }

    private static void loadV2(BufferedReader reader, List<ProfileData.Snapshot> snapshots) throws IOException {
        reader.readLine();

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] parts = line.split("\t", -1);
            if (parts.length < 11) {
                continue;
            }

            snapshots.add(new ProfileData.Snapshot(
                    parts[0],
                    parts[1].isEmpty() ? null : parts[1],
                    new ProfileData.MetricStats(
                            Long.parseLong(parts[2]),
                            Long.parseLong(parts[3]),
                            Long.parseLong(parts[4]),
                            Long.parseLong(parts[5])
                    ),
                    new ProfileData.MetricStats(
                            Long.parseLong(parts[6]),
                            Long.parseLong(parts[7]),
                            Long.parseLong(parts[8]),
                            Long.parseLong(parts[9])
                    ),
                    ProfileData.MetricAvailability.valueOf(parts[10])
            ));
        }
    }

    private static void loadLegacyV1(BufferedReader reader, List<ProfileData.Snapshot> snapshots) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] parts = line.split("\t", -1);
            if (parts.length < 6) {
                continue;
            }

            snapshots.add(new ProfileData.Snapshot(
                    parts[0],
                    parts[1].isEmpty() ? null : parts[1],
                    new ProfileData.MetricStats(
                            Long.parseLong(parts[2]),
                            Long.parseLong(parts[3]),
                            Long.parseLong(parts[4]),
                            Long.parseLong(parts[5])
                    ),
                    ProfileData.MetricStats.empty(),
                    ProfileData.MetricAvailability.DISABLED
            ));
        }
    }

    private static String sanitizeField(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static void appendSample(long[][] sampleBuckets, int[] sampleSizes, int sectionId, long value, long maxSamples) {
        long[] samples = sampleBuckets[sectionId];
        int size = sampleSizes[sectionId];
        if (samples == null) {
            samples = new long[initialSampleCapacity(maxSamples)];
            sampleBuckets[sectionId] = samples;
        } else if (size == samples.length) {
            sampleBuckets[sectionId] = java.util.Arrays.copyOf(samples, nextSampleCapacity(samples.length, maxSamples, size + 1));
            samples = sampleBuckets[sectionId];
        }

        samples[size] = value;
        sampleSizes[sectionId] = size + 1;
    }

    private static int initialSampleCapacity(long maxSamples) {
        if (maxSamples > 0L) {
            return (int) Math.max(1L, Math.min(8L, maxSamples));
        }
        return 8;
    }

    private static int nextSampleCapacity(int currentCapacity, long maxSamples, int requiredCapacity) {
        long nextCapacity = Math.max(1L, currentCapacity);
        while (nextCapacity < requiredCapacity) {
            nextCapacity *= 2L;
        }
        if (maxSamples > 0L) {
            nextCapacity = Math.min(nextCapacity, maxSamples);
        }
        return (int) Math.max(nextCapacity, requiredCapacity);
    }

    private static long[] mergedSortedSamples(long generation, int sectionId, boolean executionMetric, int sampleCount) {
        long[] merged = new long[sampleCount];
        int offset = 0;

        for (ThreadState state : LIVE_STATES) {
            if (state.generation != generation) {
                continue;
            }
            int[] sampleSizes = executionMetric ? state.durationSampleSizes : state.allocationSampleSizes;
            if (sectionId >= sampleSizes.length) {
                continue;
            }

            int size = sampleSizes[sectionId];
            if (size == 0) {
                continue;
            }

            long[][] sampleBuckets = executionMetric ? state.durationSamples : state.allocationSamples;
            System.arraycopy(sampleBuckets[sectionId], 0, merged, offset, size);
            offset += size;
        }

        java.util.Arrays.sort(merged);
        return merged;
    }

    private static long medianOf(long[] sortedSamples) {
        if (sortedSamples.length == 0) {
            return 0L;
        }
        int middle = sortedSamples.length / 2;
        if ((sortedSamples.length & 1) == 1) {
            return sortedSamples[middle];
        }
        return Math.round((sortedSamples[middle - 1] + sortedSamples[middle]) / 2.0);
    }

    private static long percentileOf(long[] sortedSamples, double percentile) {
        if (sortedSamples.length == 0) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile * sortedSamples.length) - 1;
        index = Math.max(0, Math.min(index, sortedSamples.length - 1));
        return sortedSamples[index];
    }
}
