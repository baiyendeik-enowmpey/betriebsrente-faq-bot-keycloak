package de.team.betriebsrente.model;

import jakarta.persistence.*;

@Entity
@Table(name = "fachwissen_chunks")
public class FachwissenChunks {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "fachwissen_id")
    private Long fachwissenId;
    @Column(name = "chunk_text", columnDefinition = "TEXT")
    private String chunkText;

    public Long getId() {
        return id;
    }

    public Long getFachwissenId() {
        return fachwissenId;
    }
}
