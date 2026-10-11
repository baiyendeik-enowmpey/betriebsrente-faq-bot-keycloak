package de.team.betriebsrente.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ChunkingService {
    @Value("${app.chunk.size:650}")
    int chunkSize;
    @Value("${app.chunk.overlap:130}")
    int overlap;

    @NonNull
    public List<Document> chunk(@NonNull String originalText, @NonNull Map<String, Object> baseMetadata) {
        TokenTextSplitter splitter = new TokenTextSplitter(chunkSize, 350, 5, 10000, true);
        List<Document> docs = splitter.apply(List.of(new Document(originalText, baseMetadata)));
        List<Document> enriched = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            String text = Objects.requireNonNullElse(d.getText(), "");
            Map<String, Object> meta = new HashMap<>(d.getMetadata());
            meta.put("chunk_index", i);
            meta.put("chunk_count", docs.size());
            meta.put("token_estimate", text.length() / 4);
            enriched.add(new Document(text, meta));
        }
        return enriched;
    }
}
