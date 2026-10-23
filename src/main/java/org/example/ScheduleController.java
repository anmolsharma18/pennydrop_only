package org.example;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ScheduleController {
    private final ScheduleService schedules;
    private final PortalController portal;
    public ScheduleController(ScheduleService schedules,PortalController portal){this.schedules=schedules;this.portal=portal;}
    @GetMapping("/scheduler") public Map<String,Object> health(){return schedules.health();}
    @GetMapping("/schedules") public List<Map<String,Object>> list(){return schedules.list();}
    @PostMapping("/schedules") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@Valid @RequestBody ScheduleRequest request,
            @RequestHeader(value="X-Portal-Token",required=false) String token,@RequestHeader(value="Origin",required=false) String origin){
        portal.authorize(token,origin);return schedules.create(request);
    }
    @GetMapping("/schedules/{id}/inputs") public Map<String,Object> inputs(@PathVariable String id,
            @RequestHeader(value="X-Portal-Token",required=false) String token,@RequestHeader(value="Origin",required=false) String origin){
        portal.authorize(token,origin);return schedules.inputs(id);
    }
    @PostMapping("/schedules/{id}/cancel") public Map<String,Object> cancel(@PathVariable String id,
            @RequestHeader(value="X-Portal-Token",required=false) String token,@RequestHeader(value="Origin",required=false) String origin){
        portal.authorize(token,origin);return schedules.cancel(id);
    }
    @DeleteMapping("/schedules/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id,
            @RequestHeader(value="X-Portal-Token",required=false) String token,@RequestHeader(value="Origin",required=false) String origin){
        portal.authorize(token,origin);schedules.delete(id);
    }
    @GetMapping("/schedules/{id}/report") public Map<String,Object> report(@PathVariable String id){return schedules.report(id);}
}
