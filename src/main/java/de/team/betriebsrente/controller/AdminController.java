package de.team.betriebsrente.controller;

import de.team.betriebsrente.model.Fachwissen;
import de.team.betriebsrente.model.Gueltigkeit;
import de.team.betriebsrente.rag.*;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private final DocumentParserService parser;
    private final ChunkingService chunking;
    private final VectorStore vectorStore;
    private final EntityManager em;

    public AdminController(DocumentParserService parser, ChunkingService chunking, VectorStore vectorStore,
            EntityManager em) {
        this.parser = parser;
        this.chunking = chunking;
        this.vectorStore = vectorStore;
        this.em = em;
    }

    @GetMapping
    public String admin(Model model) {
        List<Fachwissen> list = em.createQuery("SELECT f FROM Fachwissen f ORDER BY f.createdAt DESC", Fachwissen.class)
                .setMaxResults(50).getResultList();
        model.addAttribute("dokumente", list);
        try {
            Object cnt = em.createNativeQuery("SELECT COUNT(*) FROM vector_store").getSingleResult();
            model.addAttribute("chunkCount", cnt);
        } catch (Exception e) {
            model.addAttribute("chunkCount", "?");
        }
        model.addAttribute("docCount", em.createQuery("SELECT COUNT(f) FROM Fachwissen f").getSingleResult());
        model.addAttribute("abgelaufenCount", em.createQuery(
                        "SELECT COUNT(f) FROM Fachwissen f WHERE f.gueltigBis < :heute")
                .setParameter("heute", LocalDate.now()).getSingleResult());
        model.addAttribute("kategorien", em.createQuery(
                "SELECT DISTINCT f.kategorie FROM Fachwissen f ORDER BY f.kategorie", String.class).getResultList());
        return "admin";
    }

    @PostMapping("/upload")
    @Transactional
    @ResponseBody
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
            @RequestParam("kategorie") String kategorie,
            @RequestParam("quelle") String quelle,
            @RequestParam(value = "titel", required = false) String titel,
            @RequestParam(value = "gueltigAb", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate gueltigAb,
            @RequestParam(value = "gueltigBis", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate gueltigBis) {
        Map<String, Object> resp = new HashMap<>();
        String kategorieBereinigt = kategorie == null ? "" : kategorie.strip();
        if (kategorieBereinigt.isEmpty()) {
            resp.put("error", "Bitte eine Kategorie angeben.");
            return resp;
        }
        String gueltigkeitsFehler = Gueltigkeit.pruefe(gueltigAb, gueltigBis);
        if (gueltigkeitsFehler != null) {
            resp.put("error", gueltigkeitsFehler);
            return resp;
        }
        try {
            var parsed = parser.parse(file);
            String parsedText = Objects.requireNonNullElse(parsed.text(), "");
            if (parsedText.length() < 50) {
                resp.put("error", "Datei enthält zu wenig Text - gescanntes PDF? Bitte OCR vorher.");
                return resp;
            }
            Fachwissen fw = new Fachwissen();
            fw.setTitel((titel == null || titel.isBlank()) ? parsed.fileName() : titel);
            fw.setKategorie(kategorieBereinigt);
            fw.setGueltigAb(gueltigAb);
            fw.setGueltigBis(gueltigBis);
            fw.setQuelle(quelle.isBlank() ? parsed.fileName() : quelle);
            fw.setOriginalText(parsedText);
            fw.setErstelltVon("admin-upload");
            em.persist(fw);
            em.flush();

            Map<String, Object> meta = new HashMap<>();
            meta.put("fachwissen_id", fw.getId());
            meta.put("titel", fw.getTitel());
            meta.put("kategorie", fw.getKategorie());
            meta.put("quelle", fw.getQuelle());
            meta.put("fileName", parsed.fileName());
            Gueltigkeit.inMetadaten(meta, gueltigAb, gueltigBis);

            List<Document> chunks = chunking.chunk(parsedText, meta);
            vectorStore.add(chunks);

            resp.put("success", true);
            resp.put("titel", fw.getTitel());
            resp.put("chars", parsedText.length());
            resp.put("chunks", chunks.size());
            resp.put("pages", parsed.pages());
            resp.put("message", String.format("'%s' importiert: %d Zeichen -> %d Chunks",
                    fw.getTitel(), parsedText.length(), chunks.size()));
        } catch (Exception e) {
            log.error("Upload fehlgeschlagen: {}", file.getOriginalFilename(), e);
            resp.put("error", e.getMessage());
        }
        return resp;
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseBody
    public Map<String, String> delete(@PathVariable Long id) {
        try {
            // Embeddings liegen in der Spring-AI-Tabelle vector_store (nicht in fachwissen_chunks).
            // Ohne dieses Delete würden gelöschte Dokumente weiterhin als Quelle gefunden.
            vectorStore.delete(new FilterExpressionBuilder().eq("fachwissen_id", id).build());
            em.createNativeQuery("DELETE FROM fachwissen_chunks WHERE fachwissen_id = :id").setParameter("id", id)
                    .executeUpdate();
            em.createQuery("DELETE FROM Fachwissen f WHERE f.id = :id").setParameter("id", id).executeUpdate();
            return Map.of("success", "gelöscht");
        } catch (Exception e) {
            log.error("Löschen von Fachwissen {} fehlgeschlagen", id, e);
            return Map.of("error", String.valueOf(e.getMessage()));
        }
    }
}
