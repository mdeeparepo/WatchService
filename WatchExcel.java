import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class WatchExcel {
    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        String configuredDirectory = System.getenv().getOrDefault(
            "EXCEL_DIR",
            "excel-files");
        Path excelDirectory = Path.of(configuredDirectory);
        if (!excelDirectory.isAbsolute()) {
            excelDirectory = projectRoot.resolve(excelDirectory).normalize();
        }
        Files.createDirectories(excelDirectory);

        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            excelDirectory.register(
                    watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            System.out.println("Watching Excel files in: " + excelDirectory);
            while (true) {
                WatchKey key = watcher.take();
                boolean excelChanged = hasExcelChange(key);
                resetKey(key);

                if (!excelChanged) {
                    continue;
                }

                while (true) {
                    key = watcher.poll(1, TimeUnit.SECONDS);
                    if (key == null) {
                        break;
                    }
                    excelChanged |= hasExcelChange(key);
                    resetKey(key);
                }

                if (excelChanged) {
                    runPlaywright(projectRoot);
                }
            }
        }
    }

    private static boolean hasExcelChange(WatchKey key) {
        boolean excelChanged = false;
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }

            Path fileName = (Path) event.context();
            String lowerName = fileName.toString().toLowerCase(Locale.ROOT);
            if (!lowerName.startsWith("~$")
                    && (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xlsm"))) {
                excelChanged = true;
            }
        }
        return excelChanged;
    }

    private static void resetKey(WatchKey key) {
        if (!key.reset()) {
            throw new IllegalStateException("The Excel folder is no longer available to watch.");
        }
    }

    private static void runPlaywright(Path projectRoot) throws IOException, InterruptedException {
        System.out.println("Excel file changed. Running the Playwright Excel test...");
        ProcessBuilder processBuilder = new ProcessBuilder(
                "cmd.exe", "/c", "npm.cmd", "run", "test:excel");
        processBuilder.directory(projectRoot.toFile());
        processBuilder.environment().put("EXCEL_DIR", excelDirectoryFor(projectRoot).toString());
        processBuilder.inheritIO();

        int exitCode = processBuilder.start().waitFor();
        System.out.println("Playwright finished with exit code: " + exitCode);
    }

    private static Path excelDirectoryFor(Path projectRoot) {
        String configuredDirectory = System.getenv().getOrDefault(
                "EXCEL_DIR",
            "excel-files");
        Path directory = Path.of(configuredDirectory);
        return directory.isAbsolute() ? directory : projectRoot.resolve(directory).normalize();
    }
}