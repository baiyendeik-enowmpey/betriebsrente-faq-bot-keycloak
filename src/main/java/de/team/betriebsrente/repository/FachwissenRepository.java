package de.team.betriebsrente.repository;

import de.team.betriebsrente.model.Fachwissen;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FachwissenRepository extends JpaRepository<Fachwissen, Long> {

    /** Alle tatsächlich vorhandenen Kategorien, alphabetisch – Basis für den Chat-Filter. */
    @Query("SELECT DISTINCT f.kategorie FROM Fachwissen f WHERE f.kategorie IS NOT NULL ORDER BY f.kategorie")
    List<String> findKategorien();
}
