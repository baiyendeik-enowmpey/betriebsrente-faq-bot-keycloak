package de.team.betriebsrente.rag;

import de.team.betriebsrente.model.AuditLog;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final EntityManager em;

    public AuditService(EntityManager em) {
        this.em = em;
    }

    @Transactional
    public void log(String action, String fileName, String kategorie, String details, Boolean ocrUsed, Integer chunks,
            Integer chars, HttpServletRequest request) {
        String username = "anonymous";
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null)
                username = auth.getName();
        } catch (Exception ignored) {
        }
        AuditLog log = new AuditLog();
        log.setUsername(username);
        log.setAction(action);
        log.setFileName(fileName);
        log.setKategorie(kategorie);
        log.setDetails(details);
        log.setOcrUsed(ocrUsed != null ? ocrUsed : false);
        log.setChunkCount(chunks);
        log.setCharCount(chars);
        if (request != null) {
            log.setIpAddress(request.getRemoteAddr());
            log.setUserAgent(request.getHeader("User-Agent"));
        }
        em.persist(log);
        System.out.printf("[AUDIT] %s | %s | %s%n", username, action, fileName != null ? fileName : kategorie);
    }

    @Transactional
    public void logChat(String sessionId, String frage, String antwort, String kategorie, String quellen,
            String username) {
        var chat = new de.team.betriebsrente.model.ChatHistory();
        chat.setSessionId(sessionId != null ? sessionId : "default");
        chat.setUsername(username);
        chat.setFrage(frage);
        chat.setAntwort(antwort);
        chat.setKategorie(kategorie);
        chat.setQuellen(quellen);
        em.persist(chat);
    }
}
