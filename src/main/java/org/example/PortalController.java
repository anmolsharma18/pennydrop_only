package org.example;

import jakarta.validation.Valid;
import java.util.*;
import java.nio.file.Files;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class PortalController {
    private final RunService runs;
    private final PortalSettings settings;
    private final ScheduleService schedules;
    private final AdminCleanupProperties admin;
    private final SavedDetailsService details;
    private final SuccessNotifier notifications;
    private final String token=UUID.randomUUID().toString();
    public PortalController(RunService runs,PortalSettings settings,ScheduleService schedules,AdminCleanupProperties admin,SavedDetailsService details,SuccessNotifier notifications) { this.runs=runs; this.settings=settings; this.schedules=schedules;this.admin=admin;this.details=details;this.notifications=notifications; }
    @GetMapping("/saved-details") public Map<String,Object> savedDetails(@RequestHeader(value="X-Portal-Token",required=false) String supplied,@RequestHeader(value="Origin",required=false) String origin){authorize(supplied,origin);return Map.of("values",details.load(),"emailConfigured",notifications.configured());}
    @PutMapping("/saved-details") public Map<String,String> saveDetails(@RequestHeader(value="X-Portal-Token",required=false) String supplied,@RequestHeader(value="Origin",required=false) String origin,@RequestBody Map<String,String> values){authorize(supplied,origin);return details.save(values);}
    @DeleteMapping("/saved-details") public Map<String,String> clearDetails(@RequestHeader(value="X-Portal-Token",required=false) String supplied,@RequestHeader(value="Origin",required=false) String origin){authorize(supplied,origin);return details.clear();}
    @GetMapping("/config") public Map<String,Object> config() {
        return Map.of("defaults",settings.defaults(),"runToken",token,"target",KycJourney.TARGET,
                "chromeVersion",settings.chromeVersion(),"adminCleanupEnabled",admin.enabled(),"locatorWarning","Post-OTP locators need live DOM verification. No live journey or admin deletion was submitted during development.");
    }
    @GetMapping("/runs") public List<Map<String,Object>> list() { return runs.list(); }
    @GetMapping("/runs/{id}") public Map<String,Object> get(@PathVariable String id) { return runs.get(id).view(); }
    @GetMapping("/runs/{id}/screenshot") public ResponseEntity<FileSystemResource> screenshot(@PathVariable String id) {
        requireKnownRun(id);var path=KycJourney.screenshotPath(id);
        if(!Files.isRegularFile(path))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Screenshot unavailable");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(new FileSystemResource(path));
    }
    @GetMapping("/runs/{id}/screenshots/{label}") public ResponseEntity<FileSystemResource> screenshot(@PathVariable String id,@PathVariable String label) {
        requireKnownRun(id);
        if(!RunScreenshots.LABELS.contains(label))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Screenshot unavailable");
        var path=RunScreenshots.path(id,label);
        if(!Files.isRegularFile(path))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Screenshot unavailable");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(new FileSystemResource(path));
    }
    @PostMapping("/runs/{id}/cancel") public Map<String,Object> cancel(@PathVariable String id,
            @RequestHeader(value="X-Portal-Token",required=false) String supplied,@RequestHeader(value="Origin",required=false) String origin) {
        authorize(supplied,origin);return runs.cancel(id).view();
    }
    @PostMapping("/runs") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,Object> start(@RequestHeader(value="X-Portal-Token",required=false) String supplied,
                                    @RequestHeader(value="Origin",required=false) String origin,
                                    @Valid @RequestBody RunInput input) {
        authorize(supplied,origin);
        return runs.start(input).view();
    }
    @PostMapping("/runs/delete-only") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,Object> deleteOnly(@RequestHeader(value="X-Portal-Token",required=false) String supplied,
            @RequestHeader(value="Origin",required=false) String origin,@Valid @RequestBody DeleteInput input) {
        authorize(supplied,origin);return runs.startDelete(input).view();
    }
    private void requireKnownRun(String id) {
        try { runs.get(id); } catch(ResponseStatusException e) { if(!schedules.ownsRun(id))throw e; }
    }
    void authorize(String supplied,String origin) {
        if(!token.equals(supplied))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Use this local portal's current session");
        if(origin!=null&&!origin.matches("http://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the local portal may control runs");
    }
}
