package net.sixik.ga_profiler;

import java.util.*;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Random random = new Random();
        Profiler.configureDefaultSampleLimit(5_000);
        Profiler.setDisplayUnit(Profiler.TimeUnit.MILLISECONDS);
        Profiler.Section renderScene = Profiler.register(
                "render.scene",
                "Главный проход отрисовки всей сцены (Full Scene Render)",
                0
        );
        Profiler.Section physicsUpdate = Profiler.register(
                "physics.update",
                "Симуляция физики и обработка коллизий (Physics Engine)",
                0
        );
        Profiler.Section uiRender = Profiler.register(
                "ui.render",
                "Отрисовка пользовательского интерфейса и шрифтов",
                0
        );

        System.out.println("Running Version 1.0...");
        runProfiling(random, 1.0, renderScene, physicsUpdate, uiRender);
        Profiler.dump("v1_0.dump");
        Profiler.reset();

        System.out.println("Running Version 1.1 (Optimized)...");
        runProfiling(random, 0.8, renderScene, physicsUpdate, uiRender);
        Profiler.dump("v1_1.dump");
        Profiler.reset();

        System.out.println("Generating reports...");

        List<String> specs = Arrays.asList(
                "Ryzen Threadripper 1950x",
                "RTX 5070",
                "RAM 64 gb: Gived 10 gb",
                "SSD M2"
        );

        HtmlReporter.generate("performance_report.html", Profiler.load("v1_1.dump"), specs);

        Map<String, Collection<ProfileData.Snapshot>> comparisonMap = new LinkedHashMap<>();
        comparisonMap.put("Version 1.0 (Baseline)", Profiler.load("v1_0.dump"));
        comparisonMap.put("Version 1.1 (Target)", Profiler.load("v1_1.dump"));
        comparisonMap.put("Competitor Engine", generateFakeData(random, renderScene, physicsUpdate));

        HtmlReporter.generateComparison("comparison_report.html", comparisonMap, specs);
        System.out.println("Comparison report generated: comparison_report.html");
    }

    private static void runProfiling(
            Random random,
            double multiplier,
            Profiler.Section renderScene,
            Profiler.Section physicsUpdate,
            Profiler.Section uiRender
    ) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            try (Profiler.ProfileScope scene = Profiler.scope(renderScene)) {
                Thread.sleep((long) (random.nextInt(10, 20) * multiplier));
            }

            try (Profiler.ProfileScope physics = Profiler.scope(physicsUpdate)) {
                Thread.sleep((long) (random.nextInt(5, 10) * multiplier));
            }

            try (Profiler.ProfileScope ui = Profiler.scope(uiRender)) {
                Thread.sleep((long) (random.nextInt(1, 3) * multiplier));
            }
        }
    }

    private static Collection<ProfileData.Snapshot> generateFakeData(
            Random random,
            Profiler.Section renderScene,
            Profiler.Section physicsUpdate
    ) {
        Profiler.reset();

        for (int i = 0; i < 50; i++) {
            long durationScene = (long) (random.nextInt(15, 25) * 1_000_000L);
            Profiler.addSample(renderScene, durationScene);

            long durationPhysics = (long) (random.nextInt(8, 12) * 1_000_000L);
            Profiler.addSample(physicsUpdate, durationPhysics);
        }
        return Profiler.getData();
    }
}
