package de.team.betriebsrente.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

class SecurityConfigTest {

    @Test
    void registersKeycloakAsTheOidcProvider() {
        SecurityConfig config = new SecurityConfig();
        var registrations = config.clientRegistrationRepository(
                "http://localhost:8180/realms/betriebsrente", "http://keycloak:8080/realms/betriebsrente",
                "betriebsrente-app", "secret");
        assertInstanceOf(ClientRegistrationRepository.class, registrations);
        assertEquals("http://localhost:8180/realms/betriebsrente",
                registrations.findByRegistrationId("keycloak").getProviderDetails().getIssuerUri());
    }
}
