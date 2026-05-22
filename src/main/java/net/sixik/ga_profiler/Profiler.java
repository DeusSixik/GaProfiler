package net.sixik.ga_profiler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
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

    private static final List<Section> SECTIONS = new ArrayList<>();
    private static final Map<String, Section> SECTIONS_BY_NAME = new HashMap<>();
    private static final CopyOnWriteArrayList<ThreadState> LIVE_STATES = new CopyOnWriteArrayList<>();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ThreadLocal<ThreadState> STATE = ThreadLocal.withInitial(Profiler::createThreadState);

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
        private long[] minDurations = new long[INITIAL_SECTION_CAPACITY];
        private long[] maxDurations = new long[INITIAL_SECTION_CAPACITY];
        private long[] totalDurations = new long[INITIAL_SECTION_CAPACITY];
        private long[] counts = new long[INITIAL_SECTION_CAPACITY];
        private boolean[] touched = new boolean[INITIAL_SECTION_CAPACITY];
        private int[] touchedIds = new int[INITIAL_SECTION_CAPACITY];
        private int touchedCount;
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

        recordDuration(state, sectionId, duration);
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

        recordDuration(state, section.id, durationNs);
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

        for (Section section : sections) {
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            long total = 0L;
            long count = 0L;

            for (ThreadState state : LIVE_STATES) {
                if (state.generation != generation || section.id >= state.counts.length) {
                    continue;
                }

                long stateCount = state.counts[section.id];
                if (stateCount == 0L) {
                    continue;
                }

                count += stateCount;
                total += state.totalDurations[section.id];
                min = Math.min(min, state.minDurations[section.id]);
                max = Math.max(max, state.maxDurations[section.id]);
            }

            if (count > 0L) {
                snapshots.add(new ProfileData.Snapshot(
                        section.name,
                        section.tooltip,
                        min == Long.MAX_VALUE ? 0L : min,
                        max == Long.MIN_VALUE ? 0L : max,
                        total,
                        count
                ));
            }
        }

        return snapshots;
    }

    public static void dump(String path) {
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(path))) {
            writer.write("Name\tTooltip\tMin\tMax\tTotal\tCount\n");

            for (ProfileData.Snapshot snapshot : getData()) {
                String name = sanitizeField(snapshot.getName());
                String tooltip = sanitizeField(snapshot.getTooltip());

                writer.write(name);
                writer.write('\t');
                writer.write(tooltip);
                writer.write('\t');
                writer.write(Long.toString(snapshot.getMin()));
                writer.write('\t');
                writer.write(Long.toString(snapshot.getMax()));
                writer.write('\t');
                writer.write(Long.toString(snapshot.getTotal()));
                writer.write('\t');
                writer.write(Long.toString(snapshot.getCount()));
                writer.write('\n');
            }
        } catch (IOException e) {
            System.err.println("Failed to dump profile data: " + e.getMessage());
        }
    }

    public static Collection<ProfileData.Snapshot> load(String path) {
        List<ProfileData.Snapshot> snapshots = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(Path.of(path))) {
            if (reader.readLine() == null) {
                return snapshots;
            }

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
                        Long.parseLong(parts[2]),
                        Long.parseLong(parts[3]),
                        Long.parseLong(parts[4]),
                        Long.parseLong(parts[5])
                ));
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
        state.touched = java.util.Arrays.copyOf(state.touched, newCapacity);
        state.touchedIds = java.util.Arrays.copyOf(state.touchedIds, newCapacity);
    }

    private static void recordDuration(ThreadState state, int sectionId, long duration) {
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
    }

    private static void clearThreadState(ThreadState state, long generation) {
        for (int i = 0; i < state.touchedCount; i++) {
            int sectionId = state.touchedIds[i];
            state.minDurations[sectionId] = 0L;
            state.maxDurations[sectionId] = 0L;
            state.totalDurations[sectionId] = 0L;
            state.counts[sectionId] = 0L;
            state.touched[sectionId] = false;
            state.touchedIds[i] = 0;
        }

        state.depth = 0;
        state.touchedCount = 0;
        state.generation = generation;
    }

    private static String sanitizeField(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
