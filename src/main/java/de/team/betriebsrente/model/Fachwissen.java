package de.team.betriebsrente.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fachwissen")
public class Fachwissen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String titel;
    private String kategorie;
    private String quelle;
    @Column(columnDefinition = "TEXT")
    private String originalText;
    private Integer version = 1;
    private String erstelltVon;
    @Column(name = "gueltig_ab")
    private LocalDate gueltigAb;
    @Column(name = "gueltig_bis")
    private LocalDate gueltigBis;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getTitel() {
        return titel;
    }

    public void setTitel(String t) {
        this.titel = t;
    }

    public String getKategorie() {
        return kategorie;
    }

    public void setKategorie(String k) {
        this.kategorie = k;
    }

    public String getQuelle() {
        return quelle;
    }

    public void setQuelle(String q) {
        this.quelle = q;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String t) {
        this.originalText = t;
    }

    public String getErstelltVon() {
        return erstelltVon;
    }

    public void setErstelltVon(String erstelltVon) {
        this.erstelltVon = erstelltVon;
    }

    public LocalDate getGueltigAb() {
        return gueltigAb;
    }

    public void setGueltigAb(LocalDate gueltigAb) {
        this.gueltigAb = gueltigAb;
    }

    public LocalDate getGueltigBis() {
        return gueltigBis;
    }

    public void setGueltigBis(LocalDate gueltigBis) {
        this.gueltigBis = gueltigBis;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Gültigkeitsstatus am heutigen Tag (für die Admin-Ansicht). */
    public Gueltigkeit.Status getGueltigkeitsStatus() {
        return Gueltigkeit.status(gueltigAb, gueltigBis, LocalDate.now());
    }
}
