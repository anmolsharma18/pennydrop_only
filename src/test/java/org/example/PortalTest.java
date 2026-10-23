package org.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"portal.mail.enabled=false","portal.mail.host=","portal.profile-directory=${java.io.tmpdir}/penny-profile-${random.uuid}","portal.scheduler-enabled=false","portal.scheduler-directory=${java.io.tmpdir}/penny-tests-${random.uuid}"}) @AutoConfigureMockMvc
class PortalTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean KycJourney journey; // No browser or live requests can be made by these tests.
    @MockitoBean AdminDeleteJourney deletion;
    private String token() throws Exception { return json.readTree(mvc.perform(get("/api/config")).andReturn().getResponse().getContentAsString()).get("runToken").asText(); }
    private final String valid="""
        {"mobile":"6999999999","dependency":"Fixture","name":"QA Fixture","ifsc":"TEST0000000",
        "accountNumber":"0000123","confirmAccountNumber":"0000123","accountType":"Fixture",
        "otpMode":"FIXED","fixedOtp":"0007","browserMode":"HEADLESS"}
        """;
    @Test void savesAndClearsDetailsOnlyThroughAuthorizedExplicitRequests()throws Exception{
        String t=token();String body="{\"mobile\":\"6999999999\",\"accountNumber\":\"0000123\",\"fixedOtp\":\"0007\",\"notificationEmails\":\"one@example.test; two@example.test\"}";
        mvc.perform(get("/api/saved-details")).andExpect(status().isForbidden());
        mvc.perform(put("/api/saved-details").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/saved-details").header("X-Portal-Token",t).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/saved-details").header("X-Portal-Token",t)).andExpect(status().isOk()).andExpect(jsonPath("$.values.accountNumber").value("0000123")).andExpect(jsonPath("$.values.fixedOtp").value("0007"));
        mvc.perform(put("/api/saved-details").header("X-Portal-Token",t).contentType("application/json").content(body.replace("one@example.test","bad"))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/saved-details").header("X-Portal-Token",t).header("Origin","https://other.invalid")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/saved-details").header("X-Portal-Token",t)).andExpect(status().isOk()).andExpect(jsonPath("$.notificationEmails").value(""));
        verifyNoInteractions(journey,deletion);
    }
    @Test void invalidEmailListsCannotStartOrScheduleRuns()throws Exception{
        String t=token();String invalid=valid.trim().replaceFirst("}\\s*$",",\"notificationEmails\":[\"bad\"]}");
        mvc.perform(post("/api/runs").header("X-Portal-Token",t).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/schedules").header("X-Portal-Token",t).contentType("application/json").content("{\"input\":"+invalid+",\"scheduledLocalTime\":\"2099-10-15T10:30\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(journey,deletion);
    }
    @Test void staticPortalAndConfigurationDoNotLaunchAnything() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/api/config")).andExpect(status().isOk()).andExpect(jsonPath("$.defaults.mobile").value(""));
        mvc.perform(get("/api/runs")).andExpect(status().isOk()); verifyNoInteractions(journey);
    }
    @Test void invalidInputsAndMissingTokenCannotLaunchBrowser() throws Exception {
        String t=token();
        for(String body:new String[]{valid.replace("0000123\",\"accountType", "0000999\",\"accountType"),valid.replace("0007","007"),valid.replace("HEADLESS","INVALID"),valid.replace("\"HEADLESS\"","0"),valid.replace("\"FIXED\"","0"),valid.replace("6999999999","123"),valid.replace("TEST0000000","BAD"),valid.replace("\"0000123\"","123"),valid.replace("QA Fixture","")}) {
            mvc.perform(post("/api/runs").header("X-Portal-Token",t).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/runs").contentType("application/json").content(valid)).andExpect(status().isForbidden());
        mvc.perform(post("/api/runs").header("X-Portal-Token",t).header("Origin","https://other.invalid").contentType("application/json").content(valid)).andExpect(status().isForbidden());
        verifyNoInteractions(journey);
    }
    @Test void explicitRunPreservesStringValues() throws Exception {
        mvc.perform(post("/api/runs").header("X-Portal-Token",token()).contentType("application/json").content(valid)).andExpect(status().isAccepted());
        verify(journey,timeout(1000)).execute(argThat(i->i.accountNumber().equals("0000123") && i.fixedOtp().equals("0007")),any());
    }
    @Test void scheduleSavesExistingInputsWithoutLaunchingAndRequiresAuthorization() throws Exception {
        String t=token();String body="{\"input\":"+valid+",\"scheduledLocalTime\":\"2099-10-15T10:30\"}";
        mvc.perform(post("/api/schedules").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/schedules").header("X-Portal-Token",t).contentType("application/json").content(body.replace("0007","007"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/schedules").header("X-Portal-Token",t).contentType("application/json").content(body.replace("2099-10-15","2000-10-15"))).andExpect(status().isBadRequest());
        var response=mvc.perform(post("/api/schedules").header("X-Portal-Token",t).contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.scheduledAt").value("2099-10-15T05:00:00Z")).andExpect(jsonPath("$.accountNumber").doesNotExist()).andReturn();
        String id=json.readTree(response.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/schedules/"+id+"/inputs")).andExpect(status().isForbidden());
        mvc.perform(get("/api/schedules/"+id+"/inputs").header("X-Portal-Token",t)).andExpect(status().isOk()).andExpect(jsonPath("$.input.accountNumber").value("0000123")).andExpect(jsonPath("$.input.fixedOtp").value("0007"));
        mvc.perform(post("/api/schedules/"+id+"/cancel").header("X-Portal-Token",t)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(delete("/api/schedules/"+id).header("X-Portal-Token",t)).andExpect(status().isNoContent());verifyNoInteractions(journey);
    }
    @Test void deleteOnlyValidatesMinimalInputsAndNeverInvokesTheKycJourney()throws Exception {
        String body="""
            {"mobile":"6999999999","name":"QA Fixture","ifsc":"TEST0000000","accountNumber":"0000123","browserMode":"HEADLESS"}
            """;String t=token();
        mvc.perform(post("/api/runs/delete-only").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/runs/delete-only").header("X-Portal-Token",t).header("Origin","https://other.invalid").contentType("application/json").content(body)).andExpect(status().isForbidden());
        for(String invalid:new String[]{body.replace("6999999999","123"),body.replace("TEST0000000","BAD"),body.replace("QA Fixture",""),body.replace("HEADLESS","INVALID"),body.replace("\"0000123\"","123")})mvc.perform(post("/api/runs/delete-only").header("X-Portal-Token",t).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        verifyNoInteractions(deletion,journey);
        mvc.perform(post("/api/runs/delete-only").header("X-Portal-Token",t).contentType("application/json").content(body)).andExpect(status().isAccepted()).andExpect(jsonPath("$.kind").value("DELETE_ONLY")).andExpect(jsonPath("$.otpSubmitted").value(false));
        verify(deletion,timeout(1000)).execute(argThat(i->i.accountNumber().equals("0000123")&&i.browserMode()==RunInput.BrowserMode.HEADLESS),argThat(r->r.kind==RunRecord.Kind.DELETE_ONLY));verifyNoInteractions(journey);
    }
}


