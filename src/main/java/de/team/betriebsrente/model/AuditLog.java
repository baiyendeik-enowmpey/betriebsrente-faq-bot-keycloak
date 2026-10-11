package de.team.betriebsrente.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant timestamp = Instant.now();
    private String username;
    private String action;
    @Column(columnDefinition = "TEXT")
    private String details;
    private String fileName;
    private String kategorie;
    private String ipAddress;
    @Column(columnDefinition = "TEXT")
    private String userAgent;
    private Boolean ocrUsed = false;
    private Integer chunkCount;
    private Integer charCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant t) {
        this.timestamp = t;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String u) {
        this.username = u;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String a) {
        this.action = a;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String d) {
        this.details = d;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String f) {
        this.fileName = f;
    }

    public String getKategorie() {
        return kategorie;
    }

    public void setKategorie(String k) {
        this.kategorie = k;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ip) {
        this.ipAddress = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String ua) {
        this.userAgent = ua;
    }

    public Boolean getOcrUsed() {
        return ocrUsed;
    }

    public void setOcrUsed(Boolean o) {
        this.ocrUsed = o;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer c) {
        this.chunkCount = c;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public void setCharCount(Integer c) {
        this.charCount = c;
    }
}
