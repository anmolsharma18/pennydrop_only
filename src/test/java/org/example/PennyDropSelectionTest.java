package org.example;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PennyDropSelectionTest {
    @Test void meonRoutingRemainsUnchangedAndRazorpayUsesItsOwnTenant() {
        var properties=new AdminCleanupProperties(true,
                "https://live.meon.co.in/admin/democapital","data/admin",
                "https://live.meon.co.in/admin/shashankg","data/admin/razorpay");

        assertThat(KycJourney.target(RunInput.PennyDropType.MEON)).isEqualTo(KycJourney.TARGET);
        assertThat(properties.url(RunInput.PennyDropType.MEON)).isEqualTo("https://live.meon.co.in/admin/democapital");
        assertThat(properties.credentialsDirectory(RunInput.PennyDropType.MEON)).isEqualTo("data/admin");

        assertThat(KycJourney.target(RunInput.PennyDropType.RAZORPAY)).isEqualTo("https://live.meon.co.in/shashankg/pennydrop_only");
        assertThat(properties.url(RunInput.PennyDropType.RAZORPAY)).isEqualTo("https://live.meon.co.in/admin/shashankg");
        assertThat(properties.credentialsDirectory(RunInput.PennyDropType.RAZORPAY)).isEqualTo("data/admin/razorpay");
    }

    @Test void identityCaptureAcceptsBothTenantJourneyPathsOnly() {
        assertThat(JourneyRecordIdentity.isPennyDropResponsePath("/democapital/pennydrop_only/result")).isTrue();
        assertThat(JourneyRecordIdentity.isPennyDropResponsePath("/shashankg/pennydrop_only/result")).isTrue();
        assertThat(JourneyRecordIdentity.isPennyDropResponsePath("/some-other-tenant/pennydrop_only/result")).isFalse();
    }
}
