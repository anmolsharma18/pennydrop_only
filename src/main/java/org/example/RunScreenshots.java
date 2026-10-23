package org.example;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import org.openqa.selenium.*;

final class RunScreenshots {
    static final Set<String> LABELS=Set.of("bank-popup","bank-popup-before-proceed","result-page","bank-completed","failure",
            "admin-matched-entry","admin-delete-confirmation","admin-delete-success");
    private RunScreenshots() {}
    static Path path(String id,String label) {
        if(!LABELS.contains(label))throw new IllegalArgumentException("Unknown screenshot label");
        return Path.of("target","run-artifacts",UUID.fromString(id).toString(),label+".png").toAbsolutePath();
    }
    static void capture(WebDriver driver,RunRecord run,String label) throws IOException {
        Path file=path(run.id,label);Files.createDirectories(file.getParent());
        byte[] png=((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES);
        Files.write(file,png);
        Files.write(KycJourney.screenshotPath(run.id),png); // Compatibility link for the latest screenshot.
        run.screenshot(label,"/api/runs/"+run.id+"/screenshots/"+label);
    }
}
