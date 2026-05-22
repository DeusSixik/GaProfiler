package net.sixik.ga_profiler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Random random = new Random();

        // --- СИМУЛЯЦИЯ ВЕРСИИ 1.0 ---
        System.out.println("Running Version 1.0...");
        runProfiling(random, 1.0);
        Profiler.dump("v1_0.dump");
        Profiler.reset();

        // --- СИМУЛЯЦИЯ ВЕРСИИ 1.1 (ОПТИМИЗИРОВАННАЯ) ---
        System.out.println("Running Version 1.1 (Optimized)...");
        runProfiling(random, 0.8); // 20% быстрее
        Profiler.dump("v1_1.dump");
        Profiler.reset();

        // --- ГЕНЕРАЦИЯ ОТЧЕТОВ ---
        System.out.println("Generating reports...");
        
        // Одиночный отчет для версии 1.1
        HtmlReporter.generate("performance_report.html", Profiler.load("v1_1.dump"));
        
        // Сравнительный отчет
        Map<String, Collection<ProfileData.Snapshot>> comparisonMap = new LinkedHashMap<>();
        comparisonMap.put("Version 1.0 (Baseline)", Profiler.load("v1_0.dump"));
        comparisonMap.put("Version 1.1 (Target)", Profiler.load("v1_1.dump"));
        
        // Можно добавить еще одну "загруженную" версию для теста
        comparisonMap.put("Competitor Engine", generateFakeData(random));

        HtmlReporter.generateComparison("comparison_report.html", comparisonMap);
        System.out.println("Comparison report generated: comparison_report.html");
    }

    private static void runProfiling(Random random, double multiplier) throws InterruptedException {
        Profiler.setTooltip("render.scene", "Главный проход отрисовки всей сцены (Full Scene Render)");
        Profiler.setTooltip("physics.update", "Симуляция физики и обработка коллизий (Physics Engine)");
        Profiler.setTooltip("ui.render", "Отрисовка пользовательского интерфейса и шрифтов");

        for (int i = 0; i < 50; i++) {
            try (Profiler.ProfileScope scene = Profiler.scope("render.scene")) {
                Thread.sleep((long) (random.nextInt(10, 20) * multiplier));
            }

            try (Profiler.ProfileScope physics = Profiler.scope("physics.update")) {
                Thread.sleep((long) (random.nextInt(5, 10) * multiplier));
            }

            try (Profiler.ProfileScope ui = Profiler.scope("ui.render")) {
                Thread.sleep((long) (random.nextInt(1, 3) * multiplier));
            }
        }
    }

    private static Collection<ProfileData.Snapshot> generateFakeData(Random random) {
        Profiler.reset();
        Profiler.setTooltip("render.scene", "Данные стороннего движка для сравнения");
        Profiler.setTooltip("physics.update", "Физика стороннего движка");
        
        for (int i = 0; i < 50; i++) {
            long durationScene = (long) (random.nextInt(15, 25) * 1_000_000L);
            Profiler.addSample("render.scene", durationScene);
            
            long durationPhysics = (long) (random.nextInt(8, 12) * 1_000_000L);
            Profiler.addSample("physics.update", durationPhysics);
        }
        return Profiler.getData();
    }
}