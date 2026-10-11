package de.team.betriebsrente.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LoginControllerTest {

    @Test
    void loginRendersLoginView() {
        assertEquals("login", new LoginController().login());
    }
}
