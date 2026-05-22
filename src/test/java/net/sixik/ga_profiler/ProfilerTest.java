package net.sixik.ga_profiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfilerTest {
    @Test
    void collectsSamplesFromRegisteredSection() {
        Profiler.reset();
        Profiler.Section section = Profiler.register("tick.section", "Tick work", 0);

        Profiler.push(section);
        Profiler.pop();

        Map<String, ProfileData.Snapshot> snapshots = Profiler.getData().stream()
                .collect(Collectors.toMap(ProfileData.Snapshot::getName, s -> s));

        assertEquals(1L, snapshots.get("tick.section").getCount());
        assertEquals("Tick work", snapshots.get("tick.section").getTooltip());
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

        Map<String, ProfileData.Snapshot> snapshots = Profiler.getData().stream()
                .collect(Collectors.toMap(ProfileData.Snapshot::getName, s -> s));

        assertEquals(1L, snapshots.get("parent").getCount());
        assertEquals(1L, snapshots.get("child").getCount());
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

        ProfileData.Snapshot snapshot = Profiler.getData().stream()
                .filter(s -> s.getName().equals("limited"))
                .findFirst()
                .orElseThrow();

        assertEquals(2L, snapshot.getCount());
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
        assertEquals(1L, snapshot.getCount());
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

        Map<String, ProfileData.Snapshot> snapshots = Profiler.getData().stream()
                .collect(Collectors.toMap(ProfileData.Snapshot::getName, s -> s));

        assertEquals(3L, snapshots.get("existing").getCount());
        assertEquals(3L, snapshots.get("future").getCount());
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

        ProfileData.Snapshot snapshot = Profiler.getData().stream()
                .filter(s -> s.getName().equals("threaded"))
                .findFirst()
                .orElseThrow();

        assertEquals(1L, snapshot.getCount());
    }
}
