package de.team.betriebsrente.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.team.betriebsrente.model.AuditLog;
import de.team.betriebsrente.model.ChatHistory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditServiceTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final AuditService service = new AuditService(entityManager);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logPersistsAuthenticatedUserAndRequestDetails() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin", "n/a", java.util.List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "unit-test");

        service.log("upload", "file.pdf", "Steuer", "imported", null, 2, 120, request);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(entityManager).persist(captor.capture());
        AuditLog log = captor.getValue();
        assertEquals("admin", log.getUsername());
        assertEquals("upload", log.getAction());
        assertEquals("file.pdf", log.getFileName());
        assertEquals(false, log.getOcrUsed());
        assertEquals("127.0.0.1", log.getIpAddress());
        assertEquals("unit-test", log.getUserAgent());
    }

    @Test
    void logChatUsesDefaultSessionForNullSessionId() {
        service.logChat(null, "question", "answer", "Steuer", "source", "team");

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(entityManager).persist(captor.capture());
        assertEquals("default", captor.getValue().getSessionId());
        assertEquals("question", captor.getValue().getFrage());
        assertEquals("answer", captor.getValue().getAntwort());
    }
}
