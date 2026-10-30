package org.example;

/** Local startup preflight. It opens Chrome/CDP and closes them without navigating to any URL. */
public final class ChromeConnectivityCheck {
    private ChromeConnectivityCheck(){}
    public static void main(String[] args) {
        ChromeSession.Started started=null;
        try {
            String major=System.getProperty("portal.chrome-version","151");
            PortalSettings settings=new PortalSettings(major,10,3,null,null);
            RunRecord run=new RunRecord();
            started=ChromeSession.open(ChromeSessionOptions.create(settings,RunInput.BrowserMode.HEADLESS),run);
            String actual=started.driver().getCapabilities().getBrowserVersion();
            if(!actual.startsWith(major+"."))throw new IllegalStateException("Chrome/CDP major mismatch");
            System.out.println("Chrome and DevTools connectivity verified locally. No URL was opened.");
        } finally {
            if(started!=null){try{started.network().close();}catch(RuntimeException ignored){}try{started.driver().quit();}catch(RuntimeException ignored){}}
        }
    }
}
