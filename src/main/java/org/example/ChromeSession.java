package org.example;

import java.util.*;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.*;

/** Opens Chrome and CDP together so a transient startup failure can be retried before any navigation or form action. */
final class ChromeSession {
    static final int MAX_ATTEMPTS=3;
    record Started(ChromeDriver driver,BrowserNetwork network){}
    @FunctionalInterface interface DriverFactory { ChromeDriver create(ChromeOptions options); }
    @FunctionalInterface interface NetworkFactory { BrowserNetwork create(ChromeDriver driver,NetworkLedger ledger); }
    @FunctionalInterface interface Sleeper { void sleep(long millis)throws InterruptedException; }
    private ChromeSession(){}
    static Started open(ChromeOptions options,RunRecord run) {
        return open(options,run,MAX_ATTEMPTS,ChromeDriver::new,BrowserNetwork::new,Thread::sleep);
    }
    static Started open(ChromeOptions options,RunRecord run,int maxAttempts,DriverFactory drivers,NetworkFactory networks,Sleeper sleeper) {
        List<Map<String,Object>> attempts=new ArrayList<>();
        for(int attempt=1;attempt<=maxAttempts;attempt++) {
            ChromeDriver driver=null;
            try {
                run.stage("Starting Chrome and DevTools (attempt "+attempt+" of "+maxAttempts+")");
                driver=drivers.create(options);
                BrowserNetwork network=networks.create(driver,run.network);
                attempts.add(Map.of("attempt",attempt,"result","CONNECTED"));
                run.diagnostic("chromeStartup",List.copyOf(attempts));
                return new Started(driver,network);
            } catch(WebDriverException e) {
                attempts.add(Map.of("attempt",attempt,"result","RETRYABLE_FAILURE","type",e.getClass().getSimpleName()));
                run.diagnostic("chromeStartup",List.copyOf(attempts));
                try{if(driver!=null)driver.quit();}catch(RuntimeException ignored){}
                if(attempt==maxAttempts)throw new IllegalStateException("Chrome and DevTools could not start after "+maxAttempts+" attempts",e);
                try{sleeper.sleep(1000L*attempt);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException("Chrome startup interrupted");}
            }
        }
        throw new IllegalStateException("Chrome startup did not run");
    }
}
