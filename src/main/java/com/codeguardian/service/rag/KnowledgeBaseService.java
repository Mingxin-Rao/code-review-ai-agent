package com.codeguardian.service.rag;

import com.codeguardian.repository.KnowledgeDocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Knowledge Base service (core RAG implementation)
 * <p>
 * Implements a hybrid-retrieval plus rerank strategy.
 * Uses PGVector as the vector store and maintains an in-memory BM25 index alongside it.
 * / This service implements a Hybrid Retrieval + Rerank strategy.
 * It uses PGVector as the vector database while also maintaining an in-memory BM25 index to support hybrid retrieval.
 */
@Service
@Lazy
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private VectorStore vectorStore; // Injected (PGVector)
    private final KnowledgeDocumentRepository repository;
    private final MinioStorageService minioStorageService;
    private final JdbcTemplate jdbcTemplate;
    @org.springframework.beans.factory.annotation.Value("${app.rag.vectorize-on-startup:false}")
    private boolean vectorizeOnStartup;
    
    // raw document list (used for building BM25)
    private List<KnowledgeDocument> documents = new ArrayList<>();
    
    // BM25 index structures
    private Map<String, List<Integer>> invertedIndex = new HashMap<>();
    private List<Map<String, Integer>> docTermFreqs = new ArrayList<>();
    private List<Integer> docLengths = new ArrayList<>();
    private double avgDocLength = 0;
    
    // BM25 algorithm parameters
    private static final double k1 = 1.5;
    private static final double b = 0.75;

    // Boilerplate to strip when ingesting the Alibaba Java Coding Guidelines PDF.
    // These stay in the source language on purpose: they are matched against the
    // document's own copyright banner and running header, not shown to users.
    private static final String PDF_BANNER_NO_COMMERCIAL_USE = "禁止用于商业用途";   // "not for commercial use"
    private static final String PDF_BANNER_ALL_RIGHTS_RESERVED = "违者必究";        // "all rights reserved"
    private static final String PDF_HEADER_PUBLISHER = "阿里巴巴";                  // "Alibaba"
    private static final String PDF_HEADER_HANDBOOK = "开发手册";                   // "development handbook"

    @PostConstruct
    public void init() {
        try {
            // 0. Check and Fix Vector Schema (Auto-healing for dimension mismatch)
            checkAndFixVectorSchema();

            // Skip eager vector search verification to avoid triggering embedding model download at startup

            // 1. load documents from the database
            List<KnowledgeDocument> dbDocs = repository.findAll();
            
            // check for legacy data with a missing category
            boolean hasNullCategory = false;
            for (KnowledgeDocument doc : dbDocs) {
                if (doc.getCategory() == null) {
                    doc.setCategory("CODE_STYLE"); // default value
                    repository.save(doc);
                    hasNullCategory = true;
                }
            }
            if (hasNullCategory) {
                log.info("Fixed missing categories for existing documents.");
                dbDocs = repository.findAll(); // reload
            }
            
            if (dbDocs.isEmpty()) {
                log.info("Database is empty. Loading default knowledge base from rules.json...");
                loadDefaultKnowledgeBase();
                dbDocs = repository.findAll();
            } else {
                // ensure the default rules are up to date (overwrite legacy data)
                log.info("Reloading default knowledge base to ensure data consistency...");
                loadDefaultKnowledgeBase();
                dbDocs = repository.findAll();
                
                log.info("Loaded {} documents from database.", dbDocs.size());
            }
            this.documents = dbDocs;
            
            // 2. build the BM25 index
            buildIndices();
            
        } catch (Exception e) {
            log.warn("KnowledgeBaseService initialization failed: {}", e.getMessage());
        }
        if (vectorizeOnStartup) {
            new Thread(() -> {
                try {
                    for (KnowledgeDocument doc : this.documents) {
                        vectorizeDocument(doc);
                    }
                } catch (Exception ex) {
                    log.warn("Background vectorization failed: {}", ex.getMessage());
                }
            }, "rag-vectorize-background").start();
        }
    }

    private void checkAndFixVectorSchema() {
        try {
            // Check if vector_store table exists
            Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'vector_store'", Integer.class);
            
            if (count != null && count > 0) {
                // Check embedding column type
                String type = jdbcTemplate.queryForObject(
                    "SELECT format_type(atttypid, atttypmod) FROM pg_attribute " +
                    "WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'", String.class);
                
                log.info("Current vector_store.embedding type: {}", type);
                
                // If type exists and is NOT vector(384), fix it
                if (type != null && !type.contains("(384)")) {
                    log.warn("Detected incorrect vector dimensions (expected 384). Fixing schema...");
                    
                    // Option 1: Drop table (Cleanest, but requires restart or re-init logic which is hard)
                    // Option 2: Alter table (Preserves table, but clears data)
                    
                    log.info("Truncating vector_store table...");
                    jdbcTemplate.execute("TRUNCATE TABLE vector_store");
                    
                    log.info("Altering embedding column to vector(384)...");
                    // Using USING clause to cast (though truncation makes it empty)
                    jdbcTemplate.execute("ALTER TABLE vector_store ALTER COLUMN embedding TYPE vector(384)");
                    
                    log.info("Schema fixed successfully. Please re-upload documents if needed.");
                }
            }
        } catch (Exception e) {
            log.error("Failed to check/fix vector schema: {}", e.getMessage());
            // Don't block startup
        }
    }

    /**
     * Load the default knowledge base (knowledge/rules.json)
     */
    private void loadDefaultKnowledgeBase() {
        try {
            ClassPathResource resource = new ClassPathResource("knowledge/rules.json");
            if (resource.exists()) {
                List<java.util.Map<String, Object>> items = objectMapper.readValue(resource.getInputStream(), new TypeReference<List<java.util.Map<String, Object>>>() {});

                for (java.util.Map<String, Object> item : items) {
                    String id = item.get("id") != null ? String.valueOf(item.get("id")) : UUID.randomUUID().toString();
                    String title = item.get("title") != null ? String.valueOf(item.get("title")) : "Untitled document";
                    String content = item.get("content") != null ? String.valueOf(item.get("content")) : "";
                    String solution = item.get("solution") != null ? String.valueOf(item.get("solution")) : null;
                    String catCode = item.get("category") != null ? String.valueOf(item.get("category")).toUpperCase() : "CODE_STYLE";

                    KnowledgeDocument doc = KnowledgeDocument.builder()
                            .id(id)
                            .title(title)
                            .content(content)
                            .solution(solution)
                            .category(catCode)
                            .createTime(java.time.LocalDateTime.now())
                            .metadata(java.util.Map.of("source", "default_rules"))
                            .build();
                    saveDocument(doc, false);
                }
            } else {
                log.warn("Knowledge Base file not found: knowledge/rules.json");
            }
        } catch (IOException e) {
            log.error("Failed to load Knowledge Base", e);
        }
    }
    
    /**
     * Save a document to the DB and VectorStore
     */
    private void saveDocument(KnowledgeDocument doc) {
        saveDocument(doc, true);
    }

    private void saveDocument(KnowledgeDocument doc, boolean vectorize) {
        repository.save(doc);
        this.documents.add(doc);
        if (!vectorize) {
            return;
        }
        vectorizeDocument(doc);
    }

    private void vectorizeDocument(KnowledgeDocument doc) {
        if (vectorStore == null) {
            return;
        }
        String content = doc.getTitle() + "\n" + doc.getContent();
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(
            List.of(new Document(doc.getId(), content, doc.getMetadata() != null ? new HashMap<>(doc.getMetadata()) : new HashMap<>()))
        );
        if (chunks.isEmpty()) {
            log.warn("Document splitting resulted in 0 chunks for doc: {}", doc.getTitle());
            return;
        }
        for (Document chunk : chunks) {
            chunk.getMetadata().put("source_doc_id", doc.getId());
        }
        try {
            vectorStore.add(chunks);
        } catch (Exception e) {
            log.error("Failed to add document to Vector Store: {}", e.getMessage(), e);
        }
    }

    /**
     * Upload and process a document (supports multiple formats)
     */
    public void uploadDocument(MultipartFile file) throws IOException {
        log.info("Starting document upload process for file: {}, size: {}", file.getOriginalFilename(), file.getSize());
        try {
            // Upload to MinIO
            log.info("Uploading file to MinIO...");
            String objectName = minioStorageService.uploadFile(file);
            log.info("File uploaded to MinIO. ObjectName: {}", objectName);

            log.info("Extracting text from document using Tika...");
            Resource resource = new InputStreamResource(file.getInputStream());
            TikaDocumentReader tikaReader = new TikaDocumentReader(resource);
            List<Document> tikaDocs = tikaReader.get();
            
            // Combine all text from Tika documents
            StringBuilder textBuilder = new StringBuilder();
            for (Document doc : tikaDocs) {
                textBuilder.append(doc.getContent()).append("\n");
            }
            String text = textBuilder.toString();
            log.info("Text extraction completed. Text length: {}", text.length());
            
            String id = UUID.randomUUID().toString();
            KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(id)
                .title(file.getOriginalFilename())
                .content(text)
                .category("CODE_STYLE")
                .minioBucketName(minioStorageService.getBucketName())
                .minioObjectName(objectName)
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .createTime(java.time.LocalDateTime.now())
                .metadata(Map.of(
                    "filename", file.getOriginalFilename(), 
                    "type", file.getContentType() != null ? file.getContentType() : "unknown",
                    "bucket", minioStorageService.getBucketName(),
                    "object", objectName
                ))
                .build();
            
            log.info("Saving document metadata to database and vector store...");
            // save and update the index
            saveDocument(doc);
            log.info("Document saved successfully. ID: {}", id);
            
            // rebuild BM25
            log.info("Updating BM25 index...");
            addToBM25Index(doc, this.documents.size() - 1);
            log.info("BM25 index updated.");
        } catch (Exception e) {
            log.error("Error during document upload process", e);
            throw e; 
        }
    }
    
    public Page<KnowledgeDocument> getDocuments(int page, int size, String keyword) {
        // use Sort.unsorted() because the ordering is already specified in the Repository's @Query
        Pageable pageable = PageRequest.of(page - 1, size, Sort.unsorted());
        
        if (keyword != null && !keyword.isEmpty()) {
            return repository.findByTitleContainingIgnoreCaseNullsLast(keyword, pageable);
        }
        return repository.findAllNullsLast(pageable);
    }

    public KnowledgeDocument getDocumentById(String id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * Delete a document
     */
    public void deleteDocument(String id) {
        Optional<KnowledgeDocument> docOpt = repository.findById(id);
        if (docOpt.isEmpty()) {
            return;
        }
        KnowledgeDocument doc = docOpt.get();

        // 1. Delete from MinIO
        if (doc.getMinioObjectName() != null) {
            try {
                minioStorageService.removeFile(doc.getMinioObjectName());
            } catch (Exception e) {
                log.error("Failed to delete file from MinIO: {}", e.getMessage());
            }
        }

        // 2. Delete from DB
        repository.deleteById(id);

        // 3. Update memory cache
        this.documents.removeIf(d -> d.getId().equals(id));
        
        // 4. Rebuild BM25 indices
        buildIndices();
    }

    public InputStream getFileStream(String objectName) {
        return minioStorageService.getFile(objectName);
    }

    public Map<String, Object> getStats() {
        long count = repository.count();
        // Calculate total size if possible, or just return count for now
        // Assuming we want a simple stats object
        Map<String, Object> stats = new HashMap<>();
        stats.put("documentCount", count);
        stats.put("name", "Default Knowledge Base");
        stats.put("description", "The system's default vector knowledge base, storing all uploaded coding standards and technical documents");
        stats.put("createTime", java.time.LocalDateTime.now()); // Placeholder
        return stats;
    }
    
    public List<KnowledgeDocument> getAllDocuments() {
        return this.documents;
    }

    /**
     * Build the BM25 inverted index and statistics (full rebuild)
     */
    private void buildIndices() {
        if (documents.isEmpty()) return;
        
        invertedIndex.clear();
        docTermFreqs.clear();
        docLengths.clear();
        long totalLength = 0;
        
        for (int i = 0; i < documents.size(); i++) {
            addToBM25Index(documents.get(i), i);
            totalLength += docLengths.get(i);
        }
        
        avgDocLength = (double) totalLength / documents.size();
        log.info("Built BM25 Index for {} documents", documents.size());
    }
    
    private void addToBM25Index(KnowledgeDocument doc, int index) {
        String text = (doc.getTitle() + " " + doc.getContent()).toLowerCase();
        List<String> terms = tokenize(text);
        
        Map<String, Integer> freqs = new HashMap<>();
        for (String term : terms) {
            freqs.put(term, freqs.getOrDefault(term, 0) + 1);
        }
        
        docTermFreqs.add(freqs);
        docLengths.add(terms.size());
        
        for (String term : freqs.keySet()) {
            invertedIndex.computeIfAbsent(term, k -> new ArrayList<>()).add(index);
        }
        
        // Update average length if adding incrementally (approximate)
        if (avgDocLength > 0) {
             long totalLength = (long) (avgDocLength * (documents.size() - 1)) + terms.size();
             avgDocLength = (double) totalLength / documents.size();
        }
    }

    /**
     * Simple tokenization (by whitespace and punctuation)
     * Proper Chinese word segmentation would require a more sophisticated library (e.g. Jieba, HanLP); here a simple regex is used
     */
    private List<String> tokenize(String text) {
        // match Chinese characters, English words, and numbers
        List<String> tokens = new ArrayList<>();
        // regex: match a Chinese character, or an English word/number
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]|[a-zA-Z0-9]+");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    /**
     * Perform hybrid search
     */
    public List<KnowledgeDocument> search(String query, int topK) {
        if (documents.isEmpty()) return Collections.emptyList();
        
        // 1. vector search
        List<Document> vectorResults = Collections.emptyList();
        if (vectorStore != null) {
            try {
                vectorResults = vectorStore.similaritySearch(
                    SearchRequest.query(query).withTopK(topK)
                );
            } catch (Exception e) {
                log.debug("Vector search failed (using BM25 only): {}", e.getMessage());
            }
        }
        
        // 2. BM25 search
        List<Integer> bm25Indices = searchBM25(query, topK);
        
        // 3. merge results and rerank
        return mergeAndRerank(vectorResults, bm25Indices, topK);
    }
    
    /**
     * Search for relevant snippets (returns chunked text rather than full documents)
     * used to build RAG context while avoiding token-limit overflow
     */
    public List<String> searchSnippets(String query, int topK) {
        // 1. prefer vector search to obtain precise snippets
        if (vectorStore != null) {
            try {
                List<Document> vectorResults = vectorStore.similaritySearch(
                    SearchRequest.query(query).withTopK(topK)
                );
                
                if (!vectorResults.isEmpty()) {
                    log.info("Found {} snippets via Vector Search", vectorResults.size());
                    return vectorResults.stream()
                            .map(doc -> {
                                 String title = (String) doc.getMetadata().getOrDefault("title", "");
                                 String content = sanitizeRagText(doc.getContent());
                                 String formatted = title.isEmpty() ? content : "[" + title + "]\n" + content;
                                 return formatted;
                            })
                            .filter(s -> s != null && !s.trim().isEmpty())
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("Vector search failed: {}", e.getMessage());
            }
        }
        
        // 2. if vector search yields nothing, fall back to hybrid search (but truncate content)
        log.info("Vector search empty/failed, falling back to document search");
        List<KnowledgeDocument> docs = search(query, topK);
        return docs.stream()
                .map(doc -> {
                    String content = doc.getContent();
                    String title = doc.getTitle();
                    String combined = sanitizeRagText("[" + title + "]\n" + content);
                    if (combined.length() > 800) {
                        return combined.substring(0, 800) + "... (truncated)";
                    }
                    return combined;
                })
                .filter(s -> s != null && !s.trim().isEmpty())
                .collect(Collectors.toList());
    }

    private String sanitizeRagText(String text) {
        if (text == null) return "";
        String[] lines = text.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        boolean prevBlank = false;
        for (String line : lines) {
            String normalized = line.replace('\u00A0', ' ');
            String trimmed = normalized.trim();
            boolean isPageNum = trimmed.matches("^\\d+\\s*/\\s*\\d+$");
            boolean isDashBanner = trimmed.matches("^—{2,}.*—{2,}$");
            boolean containsCopyright = trimmed.contains(PDF_BANNER_NO_COMMERCIAL_USE)
                    || trimmed.contains(PDF_BANNER_ALL_RIGHTS_RESERVED);
            boolean isHeader = trimmed.contains(PDF_HEADER_PUBLISHER)
                    && trimmed.contains("Java")
                    && trimmed.contains(PDF_HEADER_HANDBOOK);
            if (isPageNum || isDashBanner || containsCopyright || isHeader) {
                continue;
            }
            if (trimmed.isEmpty()) {
                if (!prevBlank) {
                    sb.append('\n');
                    prevBlank = true;
                }
            } else {
                sb.append(normalized).append('\n');
                prevBlank = false;
            }
        }
        return sb.toString().trim();
    }

    private List<Integer> searchBM25(String query, int topK) {
        List<String> queryTerms = tokenize(query.toLowerCase());
        Map<Integer, Double> scores = new HashMap<>();
        
        for (String term : queryTerms) {
            List<Integer> docIndices = invertedIndex.get(term);
            if (docIndices == null) continue;
            
            double idf = Math.log(1 + (documents.size() - docIndices.size() + 0.5) / (docIndices.size() + 0.5));
            
            for (Integer docIdx : docIndices) {
                // Check bounds
                if (docIdx >= docTermFreqs.size()) continue;

                int freq = docTermFreqs.get(docIdx).getOrDefault(term, 0);
                int docLen = docLengths.get(docIdx);
                
                double numerator = freq * (k1 + 1);
                double denominator = freq + k1 * (1 - b + b * (docLen / avgDocLength));
                
                scores.put(docIdx, scores.getOrDefault(docIdx, 0.0) + idf * numerator / denominator);
            }
        }
        
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<KnowledgeDocument> mergeAndRerank(List<Document> vectorDocs, List<Integer> bm25Indices, int topK) {
        Map<String, Double> rrfScores = new HashMap<>();
        int k = 60;
        
        if (vectorDocs != null) {
            for (int i = 0; i < vectorDocs.size(); i++) {
                Document doc = vectorDocs.get(i);
                // try to get source_doc_id from metadata; if absent, use docId
                String id = (String) doc.getMetadata().getOrDefault("source_doc_id", doc.getId());
                rrfScores.put(id, rrfScores.getOrDefault(id, 0.0) + 1.0 / (k + i + 1));
            }
        }
        
        for (int i = 0; i < bm25Indices.size(); i++) {
            if (bm25Indices.get(i) < documents.size()) {
                String id = documents.get(bm25Indices.get(i)).getId();
                rrfScores.put(id, rrfScores.getOrDefault(id, 0.0) + 1.0 / (k + i + 1));
            }
        }
        
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> findDocById(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    private KnowledgeDocument findDocById(String id) {
        return documents.stream().filter(d -> d.getId().equals(id)).findFirst().orElse(null);
    }
}
