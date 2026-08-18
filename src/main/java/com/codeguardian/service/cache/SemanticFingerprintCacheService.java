package com.codeguardian.service.cache;

import com.codeguardian.config.ReviewCacheProperties;
import com.codeguardian.entity.Finding;
import com.codeguardian.enums.ModelProviderEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Semantic fingerprint cache service (Redis).
 *
 * <p>Reuses review results for a code block during a review:
 * / During code review, reuses results for "code blocks":
 * <ul>
 *   <li>Unchanged: 100% hit based on the normalized SHA-256 (Exact Hash)</li>
 *   <li>Highly similar: approximate recall based on the normalized SimHash-64 + bucketing (LSH), then filtered by Hamming distance</li>
 * </ul>
 *
 * <p>The cache key includes namespaceVersion, promptVersion, language, provider and the RAG toggle,
 * isolating results across versions and strategies so entries are never reused incorrectly.
 * / The cache Key includes dimensions such as namespaceVersion, promptVersion, language, provider and the RAG toggle,
 * to isolate results across different versions/strategies and avoid incorrect reuse.
 *
 * <p>If Redis is unavailable or throws, the service degrades to "no hit, no write" and the main review flow is unaffected.
 * / When Redis is unavailable or an exception occurs, the service automatically degrades to no-hit and no-write, without affecting the main review flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticFingerprintCacheService {

    private static final String KEY_PREFIX = "review:fp";

    private final ReviewCacheProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    /**
     * Attempt to read from the semantic fingerprint cache.
     *
     * @param codeContent    code content
     * @param language       language name (used to isolate the cache namespace)
     * @param provider       model provider (used to isolate the cache namespace)
     * @param enableRag      whether RAG is enabled (used to isolate the cache namespace)
     * @param blockStartLine start line of the current code block (used to rebuild absolute line numbers after a hit)
     * @return returns findings on hit; returns Optional.empty() on miss
     */
    public Optional<List<Finding>> tryGetCachedFindings(String codeContent, String language, ModelProviderEnum provider, boolean enableRag, int blockStartLine) {
        if (!isCacheEnabled() || isBlank(codeContent)) {
            return Optional.empty();
        }

        FingerprintContext ctx = buildFingerprintContext(codeContent, language, provider, enableRag);
        CacheEntry exactEntry = safeGetEntry(ctx.getExactKey());
        if (exactEntry != null) {
            return Optional.of(toFindings(exactEntry, blockStartLine));
        }

        CacheEntry nearest = findNearestBySimHash(ctx.getScopePrefix(), ctx.getSimHash64(), ctx.getExactHash());
        if (nearest != null) {
            return Optional.of(toFindings(nearest, blockStartLine));
        }

        return Optional.empty();
    }

    /**
     * Write to the semantic fingerprint cache (primary key: Exact Hash; index: SimHash buckets).
     *
     * @param codeContent    code content
     * @param language       language name (used to isolate the cache namespace)
     * @param provider       model provider (used to isolate the cache namespace)
     * @param enableRag      whether RAG is enabled (used to isolate the cache namespace)
     * @param blockStartLine start line of the current code block (used to convert findings to relative line numbers)
     * @param findings       review results
     */
    public void storeFindings(String codeContent, String language, ModelProviderEnum provider, boolean enableRag, int blockStartLine, List<Finding> findings) {
        if (!isCacheEnabled() || isBlank(codeContent) || findings == null) {
            return;
        }

        FingerprintContext ctx = buildFingerprintContext(codeContent, language, provider, enableRag);

        CacheEntry entry = new CacheEntry();
        entry.setExactHash(ctx.getExactHash());
        entry.setSimHash64(ctx.getSimHash64());
        entry.setBlockStartLine(blockStartLine);
        entry.setFindings(toCachedFindings(findings, blockStartLine));

        String value;
        try {
            value = objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize semantic fingerprint cache entry: {}", e.getMessage());
            return;
        }

        Duration ttl = Duration.ofDays(Math.max(1, properties.getTtlDays()));

        safeRedisWrite(redis -> {
            ValueOperations<String, String> valueOps = redis.opsForValue();
            valueOps.set(ctx.getExactKey(), value, ttl);

            SetOperations<String, String> setOps = redis.opsForSet();
            List<String> bucketKeys = bucketKeys(ctx.getScopePrefix(), ctx.getSimHash64());
            for (String bucketKey : bucketKeys) {
                setOps.add(bucketKey, ctx.getExactHash());
                redis.expire(bucketKey, ttl);
            }
        });
    }

    /**
     * Recall candidates via SimHash buckets and select the most similar cache entry by Hamming distance.
     *
     * <p>This method only covers the approximate-hit path:
     * / This method only handles the "approximate hit" path:
     * <ul>
     *   <li>merge candidates from multiple buckets (with an upper limit)</li>
     *   <li>read each candidate entry, compute the Hamming distance, and pick the smallest</li>
     *   <li>if the smallest distance exceeds the threshold, treat it as a miss</li>
     * </ul>
     *
     * @param scopePrefix     cache isolation prefix (dimensions such as version/language/model/RAG)
     * @param querySimHash    SimHash of the current code block
     * @param queryExactHash  ExactHash of the current code block (used to exclude itself)
     * @return returns the most similar entry on hit; returns null on miss
     */
    private CacheEntry findNearestBySimHash(String scopePrefix, long querySimHash, String queryExactHash) {
        if (!isCacheEnabled()) {
            return null;
        }

        int maxCandidates = Math.max(1, properties.getSimhash().getMaxCandidates());
        int maxHammingDistance = Math.max(0, properties.getSimhash().getMaxHammingDistance());

        Set<String> candidates = safeRedisRead(redis -> {
            SetOperations<String, String> setOps = redis.opsForSet();
            Set<String> union = new HashSet<>();
            for (String bucketKey : bucketKeys(scopePrefix, querySimHash)) {
                Set<String> members = setOps.members(bucketKey);
                if (members == null || members.isEmpty()) {
                    continue;
                }
                for (String member : members) {
                    if (union.size() >= maxCandidates) {
                        break;
                    }
                    if (member != null && !member.isBlank() && !Objects.equals(member, queryExactHash)) {
                        union.add(member);
                    }
                }
                if (union.size() >= maxCandidates) {
                    break;
                }
            }
            return union;
        }).orElse(Collections.emptySet());

        if (candidates.isEmpty()) {
            return null;
        }

        CacheEntry best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidateExactHash : candidates) {
            String candidateKey = exactKey(scopePrefix, candidateExactHash);
            CacheEntry entry = safeGetEntry(candidateKey);
            if (entry == null) {
                continue;
            }

            int distance = SemanticFingerprintCalculator.hammingDistance64(querySimHash, entry.getSimHash64());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry;
            }
        }

        if (best == null || bestDistance > maxHammingDistance) {
            return null;
        }
        return best;
    }

    /**
     * Build the context for this cache access (language/provider/fingerprint/Key).
     *
     * <p>Centralises the "normalise + fingerprint + build key" logic so it is not duplicated or allowed to drift.
     * / This method unifies the "normalization + fingerprint computation + Key assembly" logic to avoid duplication and inconsistent behavior.
     *
     * @param codeContent code content
     * @param language    language
     * @param provider    model provider
     * @param enableRag   whether RAG is enabled
     * @return the fingerprint context
     */
    private FingerprintContext buildFingerprintContext(String codeContent, String language, ModelProviderEnum provider, boolean enableRag) {
        String normalizedLanguage = normalizeLanguage(language);
        String providerCode = provider != null ? provider.name() : "AUTO";
        String normalized = SemanticFingerprintNormalizer.normalize(codeContent, normalizedLanguage);

        String exactHash = SemanticFingerprintCalculator.sha256Hex(normalized);
        long simHash = SemanticFingerprintCalculator.simHash64(normalized);

        String scopePrefix = scopePrefix(normalizedLanguage, providerCode, enableRag);
        String exactKey = exactKey(scopePrefix, exactHash);
        return new FingerprintContext(normalizedLanguage, providerCode, exactHash, simHash, scopePrefix, exactKey);
    }

    /**
     * Read and deserialize a cache entry from Redis.
     *
     * <p>Returns null on deserialisation failure (treated as a miss) so the main flow is unaffected.
     * / Returns null on deserialization failure (treated as a miss) to avoid affecting the main flow.
     *
     * @param exactKey the primary Key corresponding to the ExactHash
     * @return the entry object; returns null if it does not exist or on failure
     */
    private CacheEntry safeGetEntry(String exactKey) {
        return safeRedisRead(redis -> {
            ValueOperations<String, String> valueOps = redis.opsForValue();
            String value = valueOps.get(exactKey);
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readValue(value, CacheEntry.class);
            } catch (Exception e) {
                return null;
            }
        }).orElse(null);
    }

    /**
     * Convert a cache entry into a list of Findings, restoring "absolute line numbers" based on the block start line.
     *
     * <p>The cache stores line numbers relative to the block start; they are shifted back by baseLine here.
     * / The cache stores relative line numbers (relative to the block start line); here they are restored by offsetting with baseLine.
     *
     * @param entry               the cache entry
     * @param currentBlockStartLine start line of the current block (>=1)
     * @return findings
     */
    private List<Finding> toFindings(CacheEntry entry, int currentBlockStartLine) {
        if (entry == null || entry.getFindings() == null) {
            return new ArrayList<>();
        }
        int baseLine = Math.max(1, currentBlockStartLine);

        List<Finding> findings = new ArrayList<>(entry.getFindings().size());
        for (CachedFinding cached : entry.getFindings()) {
            Finding f = new Finding();
            f.setSeverity(cached.getSeverity());
            f.setTitle(cached.getTitle());
            f.setLocation(cached.getLocation());
            f.setDescription(cached.getDescription());
            f.setSuggestion(cached.getSuggestion());
            f.setDiff(cached.getDiff());
            f.setCategory(cached.getCategory());
            f.setSource(cached.getSource());

            if (cached.getStartLine() != null) {
                f.setStartLine(cached.getStartLine() + baseLine);
            }
            if (cached.getEndLine() != null) {
                f.setEndLine(cached.getEndLine() + baseLine);
            }
            findings.add(f);
        }
        return findings;
    }

    /**
     * Convert a list of Findings into cacheable objects (relative line numbers).
     *
     * @param findings       findings with absolute line numbers
     * @param blockStartLine start line of the current block
     * @return the cacheable findings
     */
    private List<CachedFinding> toCachedFindings(List<Finding> findings, int blockStartLine) {
        if (findings == null || findings.isEmpty()) {
            return new ArrayList<>();
        }
        List<CachedFinding> cachedFindings = new ArrayList<>(findings.size());
        for (Finding f : findings) {
            CachedFinding cached = new CachedFinding();
            cached.setSeverity(f.getSeverity());
            cached.setTitle(f.getTitle());
            cached.setLocation(f.getLocation());
            cached.setDescription(f.getDescription());
            cached.setSuggestion(f.getSuggestion());
            cached.setDiff(f.getDiff());
            cached.setCategory(f.getCategory());
            cached.setSource(f.getSource());

            if (f.getStartLine() != null) {
                cached.setStartLine(f.getStartLine() - blockStartLine);
            }
            if (f.getEndLine() != null) {
                cached.setEndLine(f.getEndLine() - blockStartLine);
            }
            cachedFindings.add(cached);
        }
        return cachedFindings;
    }

    /**
     * Determine whether caching is available: the toggle is on and a RedisTemplate can be injected.
     *
     * @return true means cache reads/writes are allowed
     */
    private boolean isCacheEnabled() {
        if (!properties.isEnabled()) {
            return false;
        }
        return redisTemplateProvider.getIfAvailable() != null;
    }

    /**
     * Build the cache isolation prefix (including dimensions such as version, language, model and RAG).
     *
     * @param language     the normalized language
     * @param providerCode the model provider code
     * @param enableRag    whether RAG is enabled
     * @return scopePrefix
     */
    private String scopePrefix(String language, String providerCode, boolean enableRag) {
        return KEY_PREFIX
                + ":" + safeToken(properties.getNamespaceVersion())
                + ":" + safeToken(properties.getPromptVersion())
                + ":" + safeToken(language)
                + ":" + safeToken(providerCode)
                + ":" + (enableRag ? "rag1" : "rag0");
    }

    /**
     * Build the primary Key for an ExactHash entry.
     *
     * @param scopePrefix the scope prefix
     * @param exactHash   exactHash
     * @return Redis key
     */
    private String exactKey(String scopePrefix, String exactHash) {
        return scopePrefix + ":exact:" + exactHash;
    }

    /**
     * Generate the list of SimHash bucket Keys (used for candidate recall).
     *
     * @param scopePrefix the scope prefix
     * @param simHash64   simhash
     * @return bucket keys
     */
    private List<String> bucketKeys(String scopePrefix, long simHash64) {
        int segments = Math.max(1, properties.getSimhash().getSegments());
        return SemanticFingerprintCalculator.bucketKeys(scopePrefix, simHash64, segments);
    }

    /**
     * Normalize the language name to avoid Key drift caused by case differences or null values.
     *
     * @param language the language name
     * @return lower-case language; returns unknown for empty values
     */
    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "unknown";
        }
        return language.trim().toLowerCase();
    }

    /**
     * Convert a token into a safe form suitable for use in a Redis key.
     *
     * @param token any string
     * @return a safe token (returns na for empty values)
     */
    private String safeToken(String token) {
        if (token == null || token.isBlank()) {
            return "na";
        }
        return token.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Unified blank-check utility.
     *
     * @param s the string
     * @return true when null or only whitespace
     */
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Safely perform a Redis read: swallow exceptions and return empty to keep the main flow available.
     *
     * @param callback the read callback
     * @return the read result (returns empty on failure/unavailability)
     * @param <T> the type
     */
    private <T> Optional<T> safeRedisRead(RedisReadCallback<T> callback) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(callback.read(redis));
        } catch (Exception e) {
            log.warn("Semantic fingerprint cache Redis read failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Safely perform a Redis write: swallow exceptions so the main flow is unaffected.
     *
     * @param callback the write callback
     */
    private void safeRedisWrite(RedisWriteCallback callback) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            callback.write(redis);
        } catch (Exception e) {
            log.warn("Semantic fingerprint cache Redis write failed: {}", e.getMessage());
        }
    }

    @FunctionalInterface
    public interface RedisReadCallback<T> {
        T read(StringRedisTemplate redis);
    }

    @FunctionalInterface
    public interface RedisWriteCallback {
        void write(StringRedisTemplate redis);
    }

    @Data
    private static class FingerprintContext {
        private final String normalizedLanguage;
        private final String providerCode;
        private final String exactHash;
        private final long simHash64;
        private final String scopePrefix;
        private final String exactKey;
    }

    @Data
    public static class CacheEntry {
        private String exactHash;
        private long simHash64;
        private Integer blockStartLine;
        private List<CachedFinding> findings;
    }

    @Data
    public static class CachedFinding {
        private Integer severity;
        private String title;
        private String location;
        private Integer startLine;
        private Integer endLine;
        private String description;
        private String suggestion;
        private String diff;
        private String category;
        private String source;
    }

    /**
     * Semantic normalization: minimizes the impact of differences in comments, formatting, constants and identifier naming on similarity.
     */
    public static class SemanticFingerprintNormalizer {
        private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
        private static final Pattern LINE_COMMENT_SLASH = Pattern.compile("(?m)//.*?$");
        private static final Pattern LINE_COMMENT_HASH = Pattern.compile("(?m)#.*?$");
        private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'");
        private static final Pattern NUMBER_LITERAL = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
        private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");
        private static final Set<String> KEYWORDS = Set.of(
                "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "try", "catch", "finally",
                "throw", "throws", "new", "class", "interface", "enum", "extends", "implements", "public", "private", "protected", "static",
                "final", "void", "int", "long", "float", "double", "boolean", "char", "byte", "short", "null", "true", "false", "this",
                "super", "import", "package", "var", "let", "const", "function", "async", "await", "yield", "def", "lambda", "with", "as",
                "from", "in", "and", "or", "not", "pass", "raise"
        );

        /**
         * Normalize the input code and prepend a language prefix to reduce cross-language collisions.
         *
         * @param code     the original code
         * @param language language (lower-cased and used as a prefix in the fingerprint computation)
         * @return the normalized text
         */
        public static String normalize(String code, String language) {
            if (code == null || code.isBlank()) {
                return "";
            }

            String s = code;
            s = BLOCK_COMMENT.matcher(s).replaceAll(" ");
            s = LINE_COMMENT_SLASH.matcher(s).replaceAll(" ");
            s = LINE_COMMENT_HASH.matcher(s).replaceAll(" ");
            s = STRING_LITERAL.matcher(s).replaceAll(" STR ");
            s = NUMBER_LITERAL.matcher(s).replaceAll(" NUM ");

            s = IDENTIFIER.matcher(s).replaceAll(mr -> {
                String token = mr.group();
                String lower = token.toLowerCase();
                if (KEYWORDS.contains(lower)) {
                    return lower;
                }
                return "id";
            });

            s = s.replaceAll("\\s+", " ").trim();
            return (language != null ? language : "unknown") + ":" + s;
        }
    }

    /**
     * Fingerprint algorithm utilities: ExactHash (SHA-256) + SimHash64 + bucket Key generation.
     */
    public static class SemanticFingerprintCalculator {
        private static final long FNV_OFFSET_BASIS_64 = 0xcbf29ce484222325L;
        private static final long FNV_PRIME_64 = 0x100000001b3L;

        public static String sha256Hex(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
                return toHex(bytes);
            } catch (Exception e) {
                throw new IllegalStateException("SHA-256 not available", e);
            }
        }

        public static long simHash64(String normalizedText) {
            if (normalizedText == null || normalizedText.isBlank()) {
                return 0L;
            }
            String[] tokens = normalizedText.split("[^a-zA-Z0-9_]+");
            int[] bitWeights = new int[64];
            for (String token : tokens) {
                if (token == null || token.isBlank()) {
                    continue;
                }
                long hash = fnv1a64(token);
                for (int i = 0; i < 64; i++) {
                    long bit = (hash >>> i) & 1L;
                    bitWeights[i] += bit == 1L ? 1 : -1;
                }
            }
            long result = 0L;
            for (int i = 0; i < 64; i++) {
                if (bitWeights[i] > 0) {
                    result |= (1L << i);
                }
            }
            return result;
        }

        public static int hammingDistance64(long a, long b) {
            return Long.bitCount(a ^ b);
        }

        public static List<String> bucketKeys(String scopePrefix, long simHash64, int segments) {
            int seg = Math.max(1, segments);
            if (seg > 8) {
                seg = 8;
            }
            int bitsPerSegment = 64 / seg;
            List<String> keys = new ArrayList<>(seg);
            for (int i = 0; i < seg; i++) {
                int shift = i * bitsPerSegment;
                long mask = (bitsPerSegment == 64) ? -1L : ((1L << bitsPerSegment) - 1L);
                long part = (simHash64 >>> shift) & mask;
                keys.add(scopePrefix + ":bucket:" + i + ":" + Long.toUnsignedString(part));
            }
            return keys;
        }

        private static long fnv1a64(String s) {
            long hash = FNV_OFFSET_BASIS_64;
            byte[] data = s.getBytes(StandardCharsets.UTF_8);
            for (byte b : data) {
                hash ^= (b & 0xff);
                hash *= FNV_PRIME_64;
            }
            return hash;
        }

        private static String toHex(byte[] bytes) {
            char[] hexArray = "0123456789abcdef".toCharArray();
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        }
    }
}
