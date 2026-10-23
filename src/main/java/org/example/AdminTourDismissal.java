package org.example;

import org.openqa.selenium.*;

/** Bounded escalation for onboarding only; never retries a business action. */
final class AdminTourDismissal {
    static final By END = By.xpath("(//span[@title='End tour'])[1]");
    static final By VISIBLE = By.cssSelector(".guided-tour-step,.guided-tour-step-highlight,.guided-tour-step-tooltip");
    private int phase;
    private long nextAttempt;

    void dismissIfVisible(WebDriver driver, RunRecord run) {
        run.checkCancelled();
        if (!JourneyPage.visible(driver, VISIBLE)) {
            if (phase > 0) {
                run.diagnostic("adminTourDismissed", true);
                run.stage("Admin onboarding tour closed; disappearance verified");
                phase = 0;
            }
            return;
        }
        if (System.nanoTime() < nextAttempt) return;
        run.diagnostic("adminTourLocator", "(//span[@title='End tour'])[1]");
        run.diagnostic("adminTourDismissed", false);
        var matches = driver.findElements(END);
        run.diagnostic("adminTourCloseControl", matches.isEmpty() ? "absent" :
                "visible=" + matches.get(0).isDisplayed() + ", enabled=" + matches.get(0).isEnabled());
        JavascriptExecutor js = (JavascriptExecutor) driver;
        int attempt = phase++;
        nextAttempt = System.nanoTime() + java.time.Duration.ofMillis(700).toNanos();
        try {
            switch (attempt) {
                case 0 -> {
                    run.stage("Ending admin onboarding tour using supplied first End tour span");
                    run.diagnostic("adminTourMethod", "selenium-click");
                    if (!matches.isEmpty()) { matches.get(0).click(); run.diagnostic("adminTourEndClicked", true); }
                }
                case 1 -> {
                    run.stage("Tour remains open; JavaScript-clicking supplied End tour span");
                    run.diagnostic("adminTourMethod", "javascript-click");
                    if (!matches.isEmpty()) { js.executeScript("arguments[0].click();", matches.get(0)); run.diagnostic("adminTourEndClicked", true); }
                }
                case 2 -> {
                    run.stage("Tour remains open; invoking available tourguide.stop() API");
                    run.diagnostic("adminTourMethod", "tourguide-stop");
                    Object stopped = js.executeScript("if(typeof tourguide!=='undefined' && tourguide && typeof tourguide.stop==='function'){tourguide.stop();return true;}return false;");
                    run.diagnostic("adminTourStopAvailable", stopped);
                }
                case 3 -> {
                    run.stage("Force-closing remaining onboarding tour elements");
                    run.diagnostic("adminTourMethod", "force-tour-dom-cleanup");
                    // Verified Tourguide 0.2.0 wrapper classes. Do not remove loading overlays or business modals.
                    Object removed = js.executeScript("""
                        const nodes=[...document.querySelectorAll('.guided-tour-step,.guided-tour-step-highlight,.guided-tour-step-tooltip')];
                        nodes.forEach(e=>e.remove());
                        [document.documentElement,document.body].forEach(e=>e.classList.remove('guided-tour','no-scroll'));
                        return nodes.length;
                        """);
                    run.diagnostic("adminTourForcedElementsRemoved", removed);
                }
                default -> throw new IllegalStateException("Admin onboarding tour remains visible after bounded close fallbacks; stopped before further admin actions");
            }
        } catch (ElementNotInteractableException | StaleElementReferenceException e) {
            run.diagnostic("adminTourClickFailure", e.getClass().getSimpleName());
        } catch (JavascriptException e) {
            run.diagnostic("adminTourScriptFailure", e.getClass().getSimpleName());
            if (attempt >= 3) throw new IllegalStateException("Unable to force-close the onboarding tour; stopped before further admin actions", e);
        }
    }
}
