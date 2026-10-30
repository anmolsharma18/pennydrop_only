package org.example;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.chrome.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChromeSessionTest {
    @Test void retriesTransientDriverAndDevToolsStartupBeforeReturningConnectedSession(){
        RunRecord run=new RunRecord();ChromeOptions options=new ChromeOptions();
        ChromeDriver first=mock(ChromeDriver.class),second=mock(ChromeDriver.class),third=mock(ChromeDriver.class);
        BrowserNetwork network=mock(BrowserNetwork.class);AtomicInteger drivers=new AtomicInteger(),networks=new AtomicInteger(),sleeps=new AtomicInteger();
        ChromeSession.Started started=ChromeSession.open(options,run,3,
            ignored->switch(drivers.incrementAndGet()){case 1->throw new SessionNotCreatedException("fixture");case 2->second;default->third;},
            (driver,ledger)->{if(networks.incrementAndGet()==1)throw new org.openqa.selenium.devtools.DevToolsException("fixture");return network;},
            millis->sleeps.incrementAndGet());
        assertThat(started.driver()).isSameAs(third);assertThat(started.network()).isSameAs(network);
        verify(second).quit();verifyNoInteractions(first);verify(third,never()).quit();assertThat(sleeps).hasValue(2);
        assertThat(run.view().get("diagnostics").toString()).contains("RETRYABLE_FAILURE","CONNECTED","DevToolsException");
    }

    @Test void reportsBoundedFailureAndCleansEveryCreatedDriver(){
        RunRecord run=new RunRecord();ChromeDriver driver=mock(ChromeDriver.class);
        assertThatThrownBy(()->ChromeSession.open(new ChromeOptions(),run,2,ignored->driver,
            (created,ledger)->{throw new org.openqa.selenium.devtools.DevToolsException("fixture");},millis->{}))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("after 2 attempts");
        verify(driver,times(2)).quit();
    }
}
