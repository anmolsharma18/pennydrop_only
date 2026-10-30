package org.example;

import org.openqa.selenium.*;

/** Removes only the verified Tourguide onboarding UI and keeps it from racing later admin clicks. */
final class AdminTourDismissal {
    static final By END = By.xpath("(//span[@title='End tour'])[1]");
    static final By VISIBLE = By.cssSelector(".guided-tour-step,.guided-tour-step-highlight,.guided-tour-step-tooltip");
    private boolean armedReported;
    private long reportedRemoved;

    void dismissIfVisible(WebDriver driver, RunRecord run) {
        run.checkCancelled();
        try {
            @SuppressWarnings("unchecked")
            var result = (java.util.Map<String,Object>) ((JavascriptExecutor) driver).executeScript("""
                const selector='.guided-tour-step,.guided-tour-step-highlight,.guided-tour-step-tooltip';
                const closeTour=()=>{
                  const before=[...document.querySelectorAll(selector)];
                  const detected=before.some(e=>e.getClientRects().length>0) ||
                    document.documentElement.classList.contains('guided-tour') ||
                    document.body.classList.contains('guided-tour');
                  let endClicked=false, stopCalled=false;
                  const end=document.evaluate("(//span[@title='End tour'])[1]",document,null,
                    XPathResult.FIRST_ORDERED_NODE_TYPE,null).singleNodeValue;
                  if(end){try{end.click();endClicked=true;}catch(e){}}
                  try{
                    if(typeof window.tourguide!=='undefined' && window.tourguide &&
                        typeof window.tourguide.stop==='function'){
                      window.tourguide.stop();stopCalled=true;
                    }
                  }catch(e){}
                  const remaining=[...document.querySelectorAll(selector)];
                  remaining.forEach(e=>e.remove());
                  [document.documentElement,document.body].forEach(e=>{
                    if(e.classList.contains('guided-tour')) e.classList.remove('guided-tour');
                    if(e.classList.contains('no-scroll')) e.classList.remove('no-scroll');
                  });
                  if(window.__pennyDropTourGuard) window.__pennyDropTourGuard.removed+=remaining.length;
                  return {detected,endClicked,stopCalled,removed:remaining.length};
                };
                let installed=false;
                if(!window.__pennyDropTourGuard){
                  const guard={removed:0,observer:null};
                  window.__pennyDropTourGuard=guard;
                  guard.observer=new MutationObserver(()=>closeTour());
                  guard.observer.observe(document.documentElement,{childList:true,subtree:true,
                    attributes:true,attributeFilter:['class']});
                  installed=true;
                }
                const result=closeTour();
                result.installed=installed;
                result.totalRemoved=window.__pennyDropTourGuard.removed;
                return result;
                """);
            if (!armedReported || Boolean.TRUE.equals(result.get("installed"))) {
                run.diagnostic("adminTourGuardArmed", true);
                armedReported = true;
            }
            long totalRemoved=((Number)result.get("totalRemoved")).longValue();
            if (Boolean.TRUE.equals(result.get("detected")) || ((Number)result.get("removed")).longValue() > 0 || totalRemoved > reportedRemoved) {
                run.diagnostic("adminTourLocator", "(//span[@title='End tour'])[1]");
                run.diagnostic("adminTourMethod", "javascript-force-close-with-observer");
                run.diagnostic("adminTourEndClicked", result.get("endClicked"));
                run.diagnostic("adminTourStopAvailable", result.get("stopCalled"));
                run.diagnostic("adminTourForcedElementsRemoved", result.get("removed"));
                run.diagnostic("adminTourDismissed", true);
                run.stage("Admin onboarding tour force-closed; automatic tour guard remains active");
            }
            reportedRemoved=totalRemoved;
            if (JourneyPage.visible(driver, VISIBLE))
                throw new IllegalStateException("Admin onboarding tour remains visible after forced JavaScript cleanup; stopped before further admin actions");
        } catch (JavascriptException | ClassCastException e) {
            run.diagnostic("adminTourScriptFailure", e.getClass().getSimpleName());
            throw new IllegalStateException("Unable to install the admin onboarding tour guard; stopped before further admin actions", e);
        }
    }
}
