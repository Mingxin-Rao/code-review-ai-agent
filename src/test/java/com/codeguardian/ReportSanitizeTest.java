package com.codeguardian;

import com.codeguardian.service.pdf.PdfHtmlConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import java.lang.reflect.Method;

class ReportSanitizeTest {

    @Test
    void should_strip_hash_prefix_in_plain_strings() {
        String title = "#MEDIUM Avoid wildcard imports";
        String location = "####:1";

        // Mirrors the cleanup rule applied by ReportService
        String cleanTitle = title.replaceAll("^\\s*#{1,6}(?:[:\\s]*)", "").trim();
        String cleanLocation = location.replaceAll("^\\s*#{1,6}(?:[:\\s]*)", "").trim();

        Assertions.assertEquals("MEDIUM Avoid wildcard imports", cleanTitle);
        Assertions.assertEquals("1", cleanLocation);
    }

    @Test
    void should_remove_hash_prefix_in_html_nodes_for_pdf() throws Exception {
        String html = "<div>Issue details</div>" +
                "<div>#MEDIUM Avoid wildcard imports</div>" +
                "<div>####:2</div>" +
                "<div>Description: import each required class explicitly</div>";

        // Invoke the private PdfHtmlConverter.cleanMarkdownInHtml via reflection
        PdfHtmlConverter converter = new PdfHtmlConverter();
        Method method = PdfHtmlConverter.class.getDeclaredMethod("cleanMarkdownInHtml", String.class);
        method.setAccessible(true);
        String cleaned = (String) method.invoke(converter, html);

        // Verify no leading-'#' heading or "####:<digits>" pattern survives
        Assertions.assertFalse(cleaned.contains("#MEDIUM"));
        Assertions.assertFalse(cleaned.contains("####:"));
        Assertions.assertTrue(cleaned.contains("MEDIUM Avoid wildcard imports")
                || cleaned.contains("Avoid wildcard imports"));
        Assertions.assertTrue(cleaned.contains("2"));
    }
}
