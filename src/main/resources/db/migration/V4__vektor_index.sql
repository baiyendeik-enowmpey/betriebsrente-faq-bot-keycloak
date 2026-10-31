-- Protokoll, mit welchem Embedding-Modell der Vektor-Index (vector_store) aufgebaut wurde.
-- Weicht das konfigurierte Modell ab, bettet die App beim Start alle Textabschnitte neu ein.
CREATE TABLE vektor_index (
    id BIGSERIAL PRIMARY KEY,
    embedding_modell VARCHAR(200) NOT NULL,
    dimensionen INT NOT NULL,
    abschnitte INT NOT NULL,
    aufgebaut_am TIMESTAMP NOT NULL DEFAULT NOW()
);
