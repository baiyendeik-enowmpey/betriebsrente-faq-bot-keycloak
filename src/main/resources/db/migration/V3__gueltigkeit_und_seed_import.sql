-- Gültigkeitszeitraum für Fachwissen (z. B. jährlich wechselnde Rechengrößen).
-- NULL bedeutet: unbefristet bzw. ab sofort gültig.
ALTER TABLE fachwissen ADD COLUMN gueltig_ab DATE;
ALTER TABLE fachwissen ADD COLUMN gueltig_bis DATE;

-- Bisher importierte Seed-Einträge kennzeichnen (Admin-Uploads tragen 'admin-upload').
UPDATE fachwissen SET erstellt_von = 'seed' WHERE erstellt_von IS NULL;

-- Protokoll der Seed-Importe: Über die Prüfsumme wird erkannt, ob sich die Seed-Datei geändert hat.
CREATE TABLE seed_import (
    id BIGSERIAL PRIMARY KEY,
    hash VARCHAR(64) NOT NULL,
    eintraege INT NOT NULL,
    importiert_am TIMESTAMP NOT NULL DEFAULT NOW()
);
