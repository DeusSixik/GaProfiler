package net.sixik.ga_profiler;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Profiler {
    private static final Map<String, ProfileData> dataMap = new ConcurrentHashMap<>();
    
    private static final ThreadLocal<Deque<Long>> startTimeStack = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<String>> nameStack = ThreadLocal.withInitial(ArrayDeque::new);

    public static void push(String name) {
        nameStack.get().push(name);
        startTimeStack.get().push(System.nanoTime());
    }

    public static void setTooltip(String name, String tooltip) {
        dataMap.computeIfAbsent(name, ProfileData::new).setTooltip(tooltip);
    }

    public static void pop() {
        Deque<Long> startStack = startTimeStack.get();
        Deque<String> nStack = nameStack.get();
        
        if (startStack.isEmpty()) {
            return;
        }
        
        long duration = System.nanoTime() - startStack.pop();
        String name = nStack.pop();
        
        dataMap.computeIfAbsent(name, ProfileData::new).addSample(duration);
    }

    public static void addSample(String name, long durationNs) {
        dataMap.computeIfAbsent(name, ProfileData::new).addSample(durationNs);
    }

    public static Collection<ProfileData> getData() {
        return dataMap.values();
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
        startTimeStack.get().clear();
        nameStack.get().clear();
    }

    public static void dump(String path) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))) {
            oos.writeObject(new ArrayList<>(dataMap.values()));
        } catch (IOException e) {
            System.err.println("Failed to dump profile data: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static Collection<ProfileData> load(String path) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            return (Collection<ProfileData>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to load profile data: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
