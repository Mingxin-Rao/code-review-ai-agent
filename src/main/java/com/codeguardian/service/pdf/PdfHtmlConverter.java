package com.codeguardian.service.pdf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML-to-PDF converter
 * Handles low-level PDF conversion operations such as HTML preprocessing and font management
 */
@Component
@Slf4j
public class PdfHtmlConverter {

    private static final Pattern MD_PREFIX = Pattern.compile("^\\s*#{1,6}(?:[:\\s]*)");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Validate that HTML tags are balanced
     */
    private boolean validateHtmlTags(String html) {
        java.util.Stack<String> stack = new java.util.Stack<>();
        java.util.regex.Pattern tagPattern = java.util.regex.Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)(?:\\s[^>]*)?(/?)>");
        java.util.regex.Matcher matcher = tagPattern.matcher(html);

        while (matcher.find()) {
            String closingSlash = matcher.group(1);
            String tagName = matcher.group(2);
            String selfClosingSlash = matcher.group(3);

            // Skip self-closing tags and tags that do not require closing
            if (selfClosingSlash != null && !selfClosingSlash.isEmpty()) {
                continue;
            }
            if (isVoidElement(tagName)) {
                continue;
            }

            if (closingSlash != null && !closingSlash.isEmpty()) {
                // Closing tag
                if (!stack.isEmpty() && stack.peek().equalsIgnoreCase(tagName)) {
                    stack.pop();
                }
            } else {
                // Opening tag
                stack.push(tagName);
            }
        }

        if (!stack.isEmpty()) {
            log.warn("HTML tags are not properly closed: {}", stack);
        }
        return stack.isEmpty();
    }

    /**
     * Determine whether the tag is a void element (a tag that does not need closing)
     */
    private boolean isVoidElement(String tagName) {
        return tagName.equalsIgnoreCase("img") ||
               tagName.equalsIgnoreCase("br") ||
               tagName.equalsIgnoreCase("hr") ||
               tagName.equalsIgnoreCase("input") ||
               tagName.equalsIgnoreCase("meta") ||
               tagName.equalsIgnoreCase("link");
    }

    /**
     * Preprocess HTML to make it suitable for PDF generation
     */
    public String prepareHtmlForPdf(String html) {
        // 1. Remove script tags
        html = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");

        // 2. Remove external stylesheet links
        html = html.replaceAll("(?is)<link[^>]*/?>", "");

        // 3. Clean up Markdown syntax in the HTML content
        html = cleanMarkdownInHtml(html);

        // 4. Escape unescaped & characters
        html = escapeAmpersands(html);

        // 5. Expand CSS variables into actual color values
        html = expandCssVariables(html);

        // 6. Remove the "Back to review" button
        html = html.replaceAll("<a class=\"back\"[^>]*>.*?</a>", "");

        // 7. Completely remove the header row of the issue-details table
        html = html.replaceAll("<div class=\"table-hd\">[^\"]*\".*?</div>\\n", "");

        // 8. Inline the Prism code-highlighting styles
        html = inlinePrismStyles(html);

        // 9. Replace Font Awesome icons with Unicode characters
        html = replaceIcons(html);

        // 10. Normalize self-closing tags
        html = normalizeSelfClosingTags(html);

        // 11. Verify that HTML tags are properly closed
        if (!validateHtmlTags(html)) {
            log.warn("HTML tags are not properly closed after preprocessing; PDF generation may fail");
        }

        return html;
    }

    /**
     * Clean up Markdown syntax in the HTML content
     */
    private String cleanMarkdownInHtml(String html) {
        for (int i = 0; i < 5; i++) {
            html = html.replaceAll("(>)(\\s*)#{1,6}(?:[:\\s]*)", "$1$2");
            html = html.replaceAll("(>)([^<]*?)\\*\\*([^*]+?)\\*\\*([^<]*?)(<)", "$1$2$3$4$5");
            html = html.replaceAll("(>)([^<]*?)`([^`]+?)`([^<]*?)(<)", "$1$2$3$4$5");
            html = html.replaceAll("(>)([^<]*?)\\[([^\\]]+?)\\]\\([^\\)]+\\)([^<]*?)(<)", "$1$2$3$4$5");
            html = html.replaceAll("(>)([^<]*?)([-*+])(\\s+)([^<]*?)(<)", "$1$2$5$6");
            html = html.replaceAll("(>)([^<]*?)(>)(\\s+)([^<]*?)(<)", "$1$2$5$6");
            html = html.replaceAll("(>)([^<]*?)(#{1,6})([^<]*?)(<)", "$1$2$4$5");
        }

        html = html.replaceAll("(>)\\s*#+[:\\s]*", "$1");
        html = html.replaceAll("(</span>\\s*)#+[:\\s]*", "$1");
        html = html.replaceAll("(>)([^<]*?)(#{1,})([^<]*?)(<)", "$1$2$4$5");

        // Extra cleanup: cases where a '#' directly follows a tag
        html = html.replaceAll("()</span>#", "$1");  // </span>#
        html = html.replaceAll("()</div>#", "$1");   // </div>#
        html = html.replaceAll("()>#", "$1");          // >#

        // Clean up a '#' at the start of a tag
        html = html.replaceAll("#<", "<");

        return html;
    }

    /**
     * Escape unescaped & characters
     */
    private String escapeAmpersands(String html) {
        Map<String, String> entityMap = new HashMap<>();
        int placeholderIndex = 0;

        Pattern entityPattern = Pattern.compile("&(?:[a-zA-Z]+|#[0-9]+|#x[0-9a-fA-F]+);");
        Matcher entityMatcher = entityPattern.matcher(html);
        StringBuffer protectedHtml = new StringBuffer();

        while (entityMatcher.find()) {
            String entity = entityMatcher.group();
            String placeholder = "___ENTITY_" + placeholderIndex++ + "___";
            entityMap.put(placeholder, entity);
            entityMatcher.appendReplacement(protectedHtml, Matcher.quoteReplacement(placeholder));
        }
        entityMatcher.appendTail(protectedHtml);

        String escapedHtml = protectedHtml.toString().replace("&", "&amp;");

        for (Map.Entry<String, String> entry : entityMap.entrySet()) {
            escapedHtml = escapedHtml.replace(entry.getKey(), entry.getValue());
        }

        return escapedHtml;
    }

    /**
     * Expand CSS variables into actual color values
     */
    private String expandCssVariables(String html) {
        // Replace the basic CSS variables. ArialUnicode is only present when an optional
        // CJK font has been dropped into resources/fonts; sans-serif is the fallback.
        html = html.replace("*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Helvetica,Arial,sans-serif}",
                "*{box-sizing:border-box}body{margin:0;background:#0d1117;color:#c9d1d9;font-family:ArialUnicode,sans-serif}");

        // Replace the code-editor font, keeping monospace as the final fallback
        html = html.replaceAll("'Monaco','Menlo','Ubuntu Mono','Consolas','source-code-pro',monospace",
                "'Monaco','Menlo','Consolas',ArialUnicode,monospace");

        // Replace the font in the diff area
        html = html.replaceAll("font-family:Monaco,Menlo,Consolas,monospace",
                "font-family:'Monaco','Menlo','Consolas',ArialUnicode,monospace");

        // Replace all CSS variable references
        html = html.replaceAll("var\\(--bg\\)", "#0d1117");
        html = html.replaceAll("var\\(--card\\)", "#161b22");
        html = html.replaceAll("var\\(--text\\)", "#c9d1d9");
        html = html.replaceAll("var\\(--text2\\)", "#8b949e");
        html = html.replaceAll("var\\(--border\\)", "#30363d");
        html = html.replaceAll("var\\(--primary\\)", "#58a6ff");
        html = html.replaceAll("var\\(--critical\\)", "#f44336");
        html = html.replaceAll("var\\(--high\\)", "#ff9800");
        html = html.replaceAll("var\\(--medium\\)", "#ffc107");
        html = html.replaceAll("var\\(--low\\)", "#4caf50");
        html = html.replaceAll("var\\(--editor-bg\\)", "#0d1117");
        html = html.replaceAll("var\\(--editor-line-number\\)", "#6e7681");
        html = html.replaceAll("var\\(--editor-text\\)", "#c9d1d9");
        html = html.replaceAll("var\\(--editor-padding\\)", "20px");

        return html;
    }

    /**
     * Inline the Prism code-highlighting styles
     */
    private String inlinePrismStyles(String html) {
        String prismStyles = buildPrismStyles();
        return html.replace("</style>", "</style>\n" + prismStyles);
    }

    /**
     * Build the Prism style string
     */
    private String buildPrismStyles() {
        return "<style>\n" +
                "/* General styles: ensure all text uses the CJK font */\n" +
                "body {\n" +
                "  font-family: ArialUnicode, sans-serif;\n" +
                "}\n" +
                ".row, .badge, .loc, .muted, .diff-title, .panel-bd, .panel-hd {\n" +
                "  font-family: ArialUnicode, sans-serif;\n" +
                "}\n" +
                "/* Ensure the badge background and text colors render correctly */\n" +
                ".badge.critical { background: #f44336 !important; color: #fff !important; }\n" +
                ".badge.high { background: #ff9800 !important; color: #fff !important; }\n" +
                ".badge.medium { background: #ffc107 !important; color: #fff !important; }\n" +
                ".badge.low { background: #4caf50 !important; color: #fff !important; }\n" +
                "/* Remove extra badge whitespace and adjust right spacing */\n" +
                ".badge { margin: 0 !important; margin-right: 8px !important; padding: 2px 8px !important; gap: 0 !important; }\n" +
                "/* Remove extra spacing around icons inside the badge */\n" +
                ".badge i { margin: 0 !important; padding: 0 !important; display: inline !important; }\n" +
                "/* Force issue-row styles to ensure visual separation */\n" +
                ".row { border-bottom: 3px solid #30363d !important; margin-bottom: 30px !important; padding-bottom: 20px !important; }\n" +
                "/* Add a bottom margin to each child element to increase vertical spacing between rows */\n" +
                ".row .loc { margin-bottom: 12px !important; }\n" +
                ".row > div:nth-child(3) { margin-bottom: 12px !important; }\n" +
                "/* Styles for the description and suggestion labels */\n" +
                ".desc-label { color: #8b949e !important; font-weight: 600 !important; }\n" +
                ".suggest-label { color: #8b949e !important; font-weight: 600 !important; margin-top: 16px !important; display: inline-block !important; }\n" +
                "/* Prism Tomorrow Theme for PDF */\n" +
                "code[class*=\"language-\"], pre[class*=\"language-\"] {\n" +
                "  color: #c9d1d9;\n" +
                "  background: #0d1117;\n" +
                "  text-shadow: none;\n" +
                "  font-family: 'Monaco', 'Menlo', 'ArialUnicode', monospace;\n" +
                "  font-size: 12px;\n" +
                "  line-height: 1.6;\n" +
                "}\n" +
                ".code-editor-container {\n" +
                "  display: table;\n" +
                "  width: 100%;\n" +
                "  table-layout: fixed;\n" +
                "  background: #0d1117;\n" +
                "  border-collapse: collapse;\n" +
                "}\n" +
                ".line-numbers {\n" +
                "  display: table-cell;\n" +
                "  vertical-align: top;\n" +
                "  width: 50px;\n" +
                "  padding: 0 8px 0 0;\n" +
                "  text-align: right;\n" +
                "  border-right: 1px solid #30363d;\n" +
                "  font-family: 'Monaco', 'Menlo', 'ArialUnicode', monospace;\n" +
                "  font-size: 12px;\n" +
                "  line-height: 19.2px;\n" +
                "  color: #6e7681;\n" +
                "  background-color: #0d1117;\n" +
                "  white-space: pre;\n" +
                "  box-sizing: border-box;\n" +
                "}\n" +
                ".code-editor-pre {\n" +
                "  display: table-cell;\n" +
                "  vertical-align: top;\n" +
                "  margin: 0;\n" +
                "  padding: 0;\n" +
                "  font-family: 'Monaco', 'Menlo', 'ArialUnicode', monospace;\n" +
                "  font-size: 12px;\n" +
                "  line-height: 19.2px;\n" +
                "  background-color: transparent;\n" +
                "  white-space: pre;\n" +
                "  overflow-x: auto;\n" +
                "  overflow-y: hidden;\n" +
                "  box-sizing: border-box;\n" +
                "  width: auto;\n" +
                "  max-width: 100%;\n" +
                "}\n" +
                ".code-editor-wrapper {\n" +
                "  display: block;\n" +
                "  width: 100%;\n" +
                "  overflow-x: auto;\n" +
                "  overflow-y: visible;\n" +
                "  background: #0d1117;\n" +
                "  page-break-inside: avoid;\n" +
                "  break-inside: avoid;\n" +
                "  box-sizing: border-box;\n" +
                "}\n" +
                ".config-panel {\n" +
                "  page-break-inside: avoid;\n" +
                "  break-inside: avoid;\n" +
                "  margin-bottom: 12px;\n" +
                "}\n" +
                ".config-panel .panel-bd {\n" +
                "  max-height: none;\n" +
                "  overflow: visible;\n" +
                "  padding: 8px 18px;\n" +
                "  flex: 0 0 auto;\n" +
                "}\n" +
                ".panel.code-panel {\n" +
                "  page-break-before: always;\n" +
                "  break-before: page;\n" +
                "}\n" +
                ".panel.table {\n" +
                "  page-break-before: always;\n" +
                "  break-before: page;\n" +
                "}\n" +
                ".token.comment, .token.prolog, .token.doctype, .token.cdata {\n" +
                "  color: #8b949e;\n" +
                "}\n" +
                ".token.punctuation {\n" +
                "  color: #c9d1d9;\n" +
                "}\n" +
                ".token.property, .token.tag, .token.boolean, .token.number, .token.constant, .token.symbol, .token.deleted {\n" +
                "  color: #79c0ff;\n" +
                "}\n" +
                ".token.selector, .token.attr-name, .token.string, .token.char, .token.builtin, .token.inserted {\n" +
                "  color: #a5d6ff;\n" +
                "}\n" +
                ".token.operator, .token.entity, .token.url, .language-css .token.string, .style .token.string {\n" +
                "  color: #ff7b72;\n" +
                "}\n" +
                ".token.atrule, .token.attr-value, .token.keyword {\n" +
                "  color: #ff7b72;\n" +
                "}\n" +
                ".token.function, .token.class-name {\n" +
                "  color: #d2a8ff;\n" +
                "}\n" +
                ".token.regex, .token.important, .token.variable {\n" +
                "  color: #ffa657;\n" +
                "}\n" +
                "</style>\n";
    }

    /**
     * Replace Font Awesome icons with simple symbols, or remove them
     * The PDF renderer cannot display emoji, so a space is used instead
     */
    private String replaceIcons(String html) {
        // Handle the complex combined icons first
        html = html.replace("<i class=\"fas fa-shield-alt finding-icon finding-icon-shield\"></i><i class=\"fas fa-check finding-icon finding-icon-check\"></i>", "");

        // Then handle the simple single icons - use a space instead to avoid the PDF showing '#'
        html = html.replace("<i class=\"fas fa-arrow-left\"></i>", "←");
        html = html.replace("<i class=\"fas fa-shield-alt\"></i>", "");
        html = html.replace("<i class=\"fas fa-check\"></i>", "");
        html = html.replace("<i class=\"fas fa-bug\"></i>", "");
        html = html.replace("<i class=\"fas fa-cog\"></i>", "");
        html = html.replace("<i class=\"fas fa-chart-bar\"></i>", "");

        // Handle icons with different attributes (using a safer regular expression)
        html = html.replaceAll("<i class=\"fas fa-shield-alt[^\"]*\"></i>", "");
        html = html.replaceAll("<i class=\"fas fa-check[^\"]*\"></i>", "");
        html = html.replaceAll("<i class=\"fas fa-bug[^\"]*\"></i>", "");
        html = html.replaceAll("<i class=\"fas fa-cog[^\"]*\"></i>", "");
        html = html.replaceAll("<i class=\"fas fa-chart-bar[^\"]*\"></i>", "");

        return html;
    }

    /**
     * Normalize self-closing tags
     */
    private String normalizeSelfClosingTags(String html) {
        return html.replaceAll("(?i)<(meta|img|br|hr|input|area|base|col|embed|source|track|wbr)([^>]*?)(?<!\\s/)(?<!/)>", "<$1$2 />");
    }

    /**
     * Load the optional CJK font used for PDF rendering.
     *
     * <p>No font ships with the project, since the ones with full CJK coverage are not
     * redistributable. Drop a TrueType font at {@code resources/fonts/ArialUnicode.ttf}
     * to enable CJK glyphs in generated PDFs. When it is absent, PDF generation still
     * succeeds and the renderer falls back to its built-in fonts.
     *
     * @return the font file, or a failure result when no font is available
     */
    public FontLoadResult loadFont() {
        try {
            Resource fontResource = new ClassPathResource("fonts/ArialUnicode.ttf");
            if (!fontResource.exists()) {
                log.debug("No optional CJK font at fonts/ArialUnicode.ttf; using the renderer's built-in fonts");
                return FontLoadResult.failure("No optional CJK font installed");
            }

            log.info("Loading font file: fonts/ArialUnicode.ttf");
            File fontFile = tryGetFontFile(fontResource);
            if (fontFile == null) {
                log.error("Unable to obtain the font file");
                return FontLoadResult.failure("Unable to read the font file");
            }

            log.info("Font file loaded successfully: path={}, size={} bytes", fontFile.getAbsolutePath(), fontFile.length());

            // Validate the font file
            if (!validateFontFile(fontFile)) {
                log.warn("Font file validation failed, but still attempting to use it");
                // Do not return failure; let the PDF renderer try to use it
            }

            return FontLoadResult.success(fontFile);
        } catch (Exception e) {
            log.error("Error loading the CJK font: {}", e.getMessage(), e);
            return FontLoadResult.failure(e.getMessage());
        }
    }

    /**
     * Try to obtain the font file
     */
    private File tryGetFontFile(Resource fontResource) throws IOException {
        try {
            // Try to get the file directly (development environment)
            File fontFile = fontResource.getFile();
            if (fontFile.exists() && fontFile.canRead() && fontFile.length() >= 1024) {
                return fontFile;
            }
            throw new IOException("Font file is not readable");
        } catch (IOException e) {
            // If running inside a JAR, copy it to a temporary file
            return copyToTempFile(fontResource);
        }
    }

    /**
     * Copy the font to a temporary file
     */
    private File copyToTempFile(Resource fontResource) throws IOException {
        try (InputStream is = fontResource.getInputStream()) {
            if (is == null) {
                throw new IOException("Unable to read the font file input stream");
            }
            File fontFile = File.createTempFile("ArialUnicode", ".ttf");
            fontFile.deleteOnExit();
            long copied = Files.copy(is, fontFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            if (!fontFile.exists() || fontFile.length() == 0 || copied == 0) {
                throw new IOException("Failed to create the temporary font file, size: " + fontFile.length());
            }
            if (fontFile.length() < 1024) {
                throw new IOException("Temporary font file has an abnormal size: " + fontFile.length() + " bytes");
            }
            return fontFile;
        }
    }

    /**
     * Validate whether the font file is valid
     */
    private boolean validateFontFile(File fontFile) {
        if (fontFile == null || !fontFile.exists()) {
            log.warn("Font validation failed: file does not exist");
            return false;
        }

        if (fontFile.length() < 1024) {
            log.warn("Font validation failed: file size too small ({} bytes)", fontFile.length());
            return false;
        }

        try (FileInputStream fis = new FileInputStream(fontFile)) {
            byte[] header = new byte[4];
            if (fis.read(header) == 4) {
                log.info("Font file header: [{}, {}, {}, {}]",
                        String.format("0x%02X", header[0]),
                        String.format("0x%02X", header[1]),
                        String.format("0x%02X", header[2]),
                        String.format("0x%02X", header[3]));

                // Check the TTF/OTF file header
                if (header[0] == 0x00 && header[1] == 0x01 && header[2] == 0x00 && header[3] == 0x00) {
                    log.info("Font file validated: TTF format");
                    return true; // TTF
                }
                if (header[0] == 'O' && header[1] == 'T' && header[2] == 'T' && header[3] == 'O') {
                    log.info("Font file validated: OTF format");
                    return true; // OTF
                }
                if (header[0] == 't' && header[1] == 't' && header[2] == 'c' && header[3] == 'f') {
                    log.info("Font file validated: TTC format");
                    return true; // TTC
                }

                log.warn("Unknown font file format; it may not be a valid TTF/OTF file");
            }
            return false;
        } catch (IOException e) {
            log.error("Font validation failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Remove font references from the HTML (when font loading fails)
     * @deprecated use ensureSystemFontFallback instead
     */
    @Deprecated
    public String removeFontReferences(String html) {
        html = html.replace("font-family:ArialUnicode,", "font-family:");
        html = html.replace("font-family:ArialUnicode;", "font-family:sans-serif;");
        return html.replaceAll("ArialUnicode", "sans-serif");
    }

    /**
     * Ensure the system font fallback is used (when font loading fails)
     * Replace the custom font name with a system font the PDF renderer can recognize
     */
    public String ensureSystemFontFallback(String html) {
        // Replace ArialUnicode with a sequence of CJK fonts supported by the PDF renderer
        // STSong-Light, STSong, MHei, MSung, etc. are common PDF CJK fonts
        html = html.replaceAll("ArialUnicode", "STSong-Light, STSong, MHei, MSung, sans-serif");

        // Ensure the code editor also uses a font that supports CJK
        html = html.replaceAll("'Monaco', 'Menlo', 'ArialUnicode', monospace",
                "'Monaco', 'Menlo', 'STSong-Light', 'Courier New', monospace");

        return html;
    }

    /**
     * Generate the PDF file name
     */
    public String generatePdfFileName(Long taskId) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        return "review_report_" + taskId + "_" + timestamp + ".pdf";
    }

    /**
     * Font load result
     */
    public static class FontLoadResult {
        private final boolean success;
        private final File fontFile;
        private final String errorMessage;

        private FontLoadResult(boolean success, File fontFile, String errorMessage) {
            this.success = success;
            this.fontFile = fontFile;
            this.errorMessage = errorMessage;
        }

        public static FontLoadResult success(File fontFile) {
            return new FontLoadResult(true, fontFile, null);
        }

        public static FontLoadResult failure(String errorMessage) {
            return new FontLoadResult(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public File getFontFile() {
            return fontFile;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Resource interface, used to decouple from Spring's Resource
     */
    public interface Resource {
        boolean exists() throws IOException;
        File getFile() throws IOException;
        InputStream getInputStream() throws IOException;
    }

    /**
     * Spring Resource adapter
     */
    private static class SpringResourceAdapter implements Resource {
        private final org.springframework.core.io.Resource delegate;

        SpringResourceAdapter(org.springframework.core.io.Resource delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean exists() throws IOException {
            return delegate.exists();
        }

        @Override
        public File getFile() throws IOException {
            return delegate.getFile();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return delegate.getInputStream();
        }
    }

    /**
     * ClassPathResource adapter
     */
    private static class ClassPathResource implements Resource {
        private final Resource adapter;

        ClassPathResource(String location) {
            this.adapter = new SpringResourceAdapter(new org.springframework.core.io.ClassPathResource(location));
        }

        @Override
        public boolean exists() throws IOException {
            return adapter.exists();
        }

        @Override
        public File getFile() throws IOException {
            return adapter.getFile();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return adapter.getInputStream();
        }
    }
}
