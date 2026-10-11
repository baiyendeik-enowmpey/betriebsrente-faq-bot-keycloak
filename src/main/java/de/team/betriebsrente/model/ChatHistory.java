package de.team.betriebsrente.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_history")
public class ChatHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sessionId;
    private String username;
    @Column(columnDefinition = "TEXT")
    private String frage;
    @Column(columnDefinition = "TEXT")
    private String antwort;
    private String kategorie;
    @Column(columnDefinition = "TEXT")
    private String quellen;
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String s) {
        this.sessionId = s;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String u) {
        this.username = u;
    }

    public String getFrage() {
        return frage;
    }

    public void setFrage(String f) {
        this.frage = f;
    }

    public String getAntwort() {
        return antwort;
    }

    public void setAntwort(String a) {
        this.antwort = a;
    }

    public String getKategorie() {
        return kategorie;
    }

    public void setKategorie(String k) {
        this.kategorie = k;
    }

    public String getQuellen() {
        return quellen;
    }

    public void setQuellen(String q) {
        this.quellen = q;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
