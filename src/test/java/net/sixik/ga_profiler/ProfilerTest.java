package net.sixik.ga_profiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfilerTest {
    @AfterEach
    void tearDown() {
        Profiler.clearAllocationCounterOverrideForTesting();
        Profiler.setAllocationProfilingEnabled(false);
        Profiler.setDisplayUnit(Profiler.TimeUnit.MILLISECONDS);
        Profiler.reset();
    }

    @Test
    void collectsSamplesFromRegisteredSection() {
        Profiler.reset();
        Profiler.Section section = Profiler.register("tick.section", "Tick work", 0);

        Profiler.push(section);
        Profiler.pop();

        ProfileData.Snapshot snapshot = snapshotByName().get("tick.section");
        assertEquals(1L, snapshot.getExecutionTime().getCount());
        assertEquals("Tick work", snapshot.getTooltip());
        assertEquals(ProfileData.MetricAvailability.DISABLED, snapshot.getMemoryAllocationAvailability());
    }

    @Test
    void supportsNestedScopes() {
        Profiler.reset();
        Profiler.Section parent = Profiler.register("parent", null, 0);
        Profiler.Section child = Profiler.register("child", null, 0);

        try (Profiler.ProfileScope ignored = Profiler.scope(parent)) {
            try (Profiler.ProfileScope ignoredChild = Profiler.scope(child)) {
                // no-op
            }
        }

        Map<String, ProfileData.Snapshot> snapshots = snapshotByName();
        assertEquals(1L, snapshots.get("parent").getExecutionTime().getCount());
        assertEquals(1L, snapshots.get("child").getExecutionTime().getCount());
    }

    @Test
    void popWithoutPushThrows() {
        Profiler.reset();
        assertThrows(IllegalStateException.class, Profiler::pop);
    }

    @Test
    void enforcesPerSectionSampleLimit() {
        Profiler.reset();
        Profiler.Section limited = Profiler.register("limited", null, 2);

        for (int i = 0; i < 5; i++) {
            Profiler.push(limited);
            Profiler.pop();
        }

        ProfileData.Snapshot snapshot = snapshotByName().get("limited");
        assertEquals(2L, snapshot.getExecutionTime().getCount());
    }

    @Test
    void rejectsDuplicateSectionNames() {
        Profiler.reset();
        Profiler.register("duplicate", null, 0);

        assertThrows(IllegalArgumentException.class, () -> Profiler.register("duplicate", null, 0));
    }

    @Test
    void dumpAndLoadRoundTripCountAndTooltip() throws Exception {
        Profiler.reset();
        Profiler.Section section = Profiler.register("round.trip", "Tooltip", 0);

        Profiler.push(section);
        Profiler.pop();

        Path dump = Files.createTempFile("profiler", ".dump");
        Profiler.dump(dump.toString());
        Collection<ProfileData.Snapshot> loaded = Profiler.load(dump.toString());

        ProfileData.Snapshot snapshot = loaded.iterator().next();
        assertEquals("round.trip", snapshot.getName());
        assertEquals("Tooltip", snapshot.getTooltip());
        assertEquals(1L, snapshot.getExecutionTime().getCount());
        assertEquals(ProfileData.MetricAvailability.DISABLED, snapshot.getMemoryAllocationAvailability());
    }

    @Test
    void appliesBulkSampleLimitToExistingAndFutureSections() {
        Profiler.reset();
        Profiler.Section existing = Profiler.register("existing", null, 0);

        Profiler.applySampleLimitToAllSections(3);
        Profiler.Section future = Profiler.register("future", null, 0);

        for (int i = 0; i < 10; i++) {
            Profiler.push(existing);
            Profiler.pop();
            Profiler.push(future);
            Profiler.pop();
        }

        Map<String, ProfileData.Snapshot> snapshots = snapshotByName();
        assertEquals(3L, snapshots.get("existing").getExecutionTime().getCount());
        assertEquals(3L, snapshots.get("future").getExecutionTime().getCount());
    }

    @Test
    void storesConfiguredDisplayUnit() {
        Profiler.setDisplayUnit(Profiler.TimeUnit.SECONDS);
        assertEquals(Profiler.TimeUnit.SECONDS, Profiler.getDisplayUnit());
    }

    @Test
    void resetPreventsOldThreadDataFromLeakingIntoNewRun() throws Exception {
        Profiler.reset();
        Profiler.Section section = Profiler.register("threaded", null, 0);

        Thread thread = new Thread(() -> {
            Profiler.push(section);
            Profiler.pop();
        });
        thread.start();
        thread.join();

        Profiler.reset();

        Profiler.push(section);
        Profiler.pop();

        ProfileData.Snapshot snapshot = snapshotByName().get("threaded");
        assertEquals(1L, snapshot.getExecutionTime().getCount());
    }

    @Test
    void allocationProfilingIsDisabledByDefault() {
        Profiler.useAllocationCounterForTesting(sequenceCounter(true, 1_000L, 1_064L));
        Profiler.reset();
        Profiler.Section section = Profiler.register("alloc.disabled", null, 0);

        Profiler.push(section);
        Profiler.pop();

        ProfileData.Snapshot snapshot = snapshotByName().get("alloc.disabled");
        assertFalse(Profiler.isAllocationProfilingEnabled());
        assertEquals(ProfileData.MetricAvailability.DISABLED, snapshot.getMemoryAllocationAvailability());
        assertEquals(0L, snapshot.getMemoryAllocation().getCount());
    }

    @Test
    void collectsAllocatedBytesPerSectionWhenEnabled() {
        Profiler.useAllocationCounterForTesting(sequenceCounter(true, 10_000L, 10_256L));
        Profiler.setAllocationProfilingEnabled(true);
        Profiler.reset();
        Profiler.Section section = Profiler.register("alloc.enabled", "Allocation path", 0);

        Profiler.push(section);
        Profiler.pop();

        ProfileData.Snapshot snapshot = snapshotByName().get("alloc.enabled");
        assertEquals(ProfileData.MetricAvailability.COLLECTED, snapshot.getMemoryAllocationAvailability());
        assertEquals(1L, snapshot.getMemoryAllocation().getCount());
        assertEquals(256L, snapshot.getMemoryAllocation().getTotal());
        assertEquals(256.0, snapshot.getMemoryAllocation().getAvg());
    }

    @Test
    void marksMemoryAllocationAsNotSupportedWhenJvmCounterIsUnavailable() {
        Profiler.useAllocationCounterForTesting(sequenceCounter(false, 0L));
        Profiler.setAllocationProfilingEnabled(true);
        Profiler.reset();
        Profiler.Section section = Profiler.register("alloc.unsupported", null, 0);

        Profiler.push(section);
        Profiler.pop();

        ProfileData.Snapshot snapshot = snapshotByName().get("alloc.unsupported");
        assertEquals(ProfileData.MetricAvailability.NOT_SUPPORTED, snapshot.getMemoryAllocationAvailability());
        assertEquals(0L, snapshot.getMemoryAllocation().getCount());
        assertEquals(1L, snapshot.getExecutionTime().getCount());
    }

    @Test
    void dumpAndLoadRoundTripAllocationMetricsAndAvailability() throws Exception {
        Profiler.useAllocationCounterForTesting(sequenceCounter(true, 2_000L, 2_512L));
        Profiler.setAllocationProfilingEnabled(true);
        Profiler.reset();
        Profiler.Section section = Profiler.register("alloc.round.trip", "Allocation dump", 0);

        Profiler.push(section);
        Profiler.pop();

        Path dump = Files.createTempFile("profiler-allocation", ".dump");
        Profiler.dump(dump.toString());
        ProfileData.Snapshot snapshot = Profiler.load(dump.toString()).iterator().next();

        assertEquals(ProfileData.MetricAvailability.COLLECTED, snapshot.getMemoryAllocationAvailability());
        assertEquals(512L, snapshot.getMemoryAllocation().getTotal());
        assertEquals("Allocation dump", snapshot.getTooltip());
    }

    @Test
    void loadStillSupportsLegacyTimeOnlyDumpFormat() throws Exception {
        Path dump = Files.createTempFile("profiler-legacy", ".dump");
        Files.writeString(dump, """
                Name\tTooltip\tMin\tMax\tTotal\tCount
                legacy.section\tLegacy tooltip\t100\t200\t300\t2
                """);

        ProfileData.Snapshot snapshot = Profiler.load(dump.toString()).iterator().next();
        assertEquals("legacy.section", snapshot.getName());
        assertEquals(ProfileData.MetricAvailability.DISABLED, snapshot.getMemoryAllocationAvailability());
        assertEquals(300L, snapshot.getExecutionTime().getTotal());
    }

    private static Map<String, ProfileData.Snapshot> snapshotByName() {
        return Profiler.getData().stream()
                .collect(Collectors.toMap(ProfileData.Snapshot::getName, s -> s));
    }

    private static Profiler.AllocationCounter sequenceCounter(boolean supported, long... values) {
        AtomicInteger index = new AtomicInteger();
        return new Profiler.AllocationCounter() {
            @Override
            public boolean isSupported() {
                return supported;
            }

            @Override
            public long currentThreadAllocatedBytes() {
                return values[Math.min(index.getAndIncrement(), values.length - 1)];
            }
        };
    }
}
