package net.sixik.ga_profiler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Profiler {
    private static final Map<String, ProfileData> dataMap = new ConcurrentHashMap<>();
    private static final ThreadLocal<ThreadState> STATE = ThreadLocal.withInitial(ThreadState::new);

    private static class ThreadState {
        long[] startTimes = new long[256];
        String[] names = new String[256];
        int depth = 0;
    }

    public static void push(String name) {
        ThreadState state = STATE.get();

        if (state.depth == state.startTimes.length) {
            int newCapacity = state.startTimes.length * 2;
            state.startTimes = Arrays.copyOf(state.startTimes, newCapacity);
            state.names = Arrays.copyOf(state.names, newCapacity);
        }

        state.names[state.depth] = name;
        state.startTimes[state.depth] = System.nanoTime();
        state.depth++;
    }

    public static void pop() {
        ThreadState state = STATE.get();
        if (state.depth == 0) return;

        state.depth--;
        long duration = System.nanoTime() - state.startTimes[state.depth];
        String name = state.names[state.depth];

        addSample(name, duration);
    }

    public static void addSample(String name, long durationNs) {
        ProfileData data = dataMap.get(name);
        if (data == null) {
            data = dataMap.computeIfAbsent(name, ProfileData::new);
        }
        data.addSample(durationNs);
    }

    public static void setTooltip(String name, String tooltip) {
        dataMap.computeIfAbsent(name, ProfileData::new).setTooltip(tooltip);
    }

    public static ProfileScope scope(String name) {
        push(name);
        return Profiler::pop;
    }

    public interface ProfileScope extends AutoCloseable {
        @Override
        void close();
    }

    public static void reset() {
        dataMap.clear();
        STATE.get().depth = 0;
    }

    public static Collection<ProfileData.Snapshot> getData() {
        List<ProfileData.Snapshot> snapshots = new ArrayList<>();
        for (ProfileData data : dataMap.values()) {
            snapshots.add(data.takeSnapshot());
        }
        return snapshots;
    }

    public static void dump(String path) {
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(path))) {
            writer.write("Name\tTooltip\tMin\tMax\tTotal\tCount\n");

            for (ProfileData data : dataMap.values()) {
                ProfileData.Snapshot s = data.takeSnapshot();

                String name = s.name != null ? s.name.replace("\t", " ").replace("\n", " ") : "unknown";
                String tooltip = s.tooltip != null ? s.tooltip.replace("\t", " ").replace("\n", " ") : "";

                writer.write(name + "\t" + tooltip + "\t" + s.min + "\t" + s.max + "\t" + s.total + "\t" + s.count + "\n");
            }
        } catch (IOException e) {
            System.err.println("Failed to dump profile data: " + e.getMessage());
        }
    }

    public static Collection<ProfileData.Snapshot> load(String path) {
        List<ProfileData.Snapshot> snapshots = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(Path.of(path))) {
            String line = reader.readLine();
            if (line == null) return snapshots;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split("\t", -1);

                if (parts.length >= 6) {
                    String name = parts[0];
                    String tooltip = parts[1].isEmpty() ? null : parts[1];
                    long min = Long.parseLong(parts[2]);
                    long max = Long.parseLong(parts[3]);
                    long total = Long.parseLong(parts[4]);
                    int count = Integer.parseInt(parts[5]);

                    snapshots.add(new ProfileData.Snapshot(name, tooltip, min, max, total, count));
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Failed to load profile data: " + e.getMessage());
        }

        return snapshots;
    }
}
