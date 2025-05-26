package org.json.junit.milestone4.tests;

import org.json.JSONObject;
import org.json.JSONObject.JSONNode;
import org.json.XML;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Unit tests for the JSONObject.toStream() extension (Milestone 4) – JUnit 4 / Java 8.
 */
public class JSONObjectStreamTest {

    private static JSONObject catalog;

    @BeforeClass
    public static void loadXml() throws Exception {
        try (InputStream in = JSONObjectStreamTest.class.getResourceAsStream("/books.xml")) {
            assertNotNull("books.xml must be on the class-path (src/test/resources)", in);

            String xml;
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                xml = scanner.hasNext() ? scanner.next() : "";
            }

            catalog = XML.toJSONObject(xml);
        }
    }

    @Test
    public void extractTitles() {
        List<String> titles = catalog.toStream()
                .filter(n -> n.getPath().endsWith("/title"))
                .map(n -> n.getValue().toString())
                .collect(Collectors.toList());

        assertEquals(12, titles.size());
        assertTrue(titles.contains("XML Developer's Guide"));
        assertTrue(titles.contains("Visual Studio 7: A Comprehensive Guide"));
    }

    @Test
    public void findExactPath() {
        String wantedPath = "/catalog/book[0]/author";
        Optional<JSONNode> node = catalog.toStream()
                .filter(n -> n.getPath().equals(wantedPath))
                .findFirst();

        assertTrue(node.isPresent());
        assertEquals("Gambardella, Matthew", node.get().getValue());
    }

    @Test
    public void filterCheapBooks() {
        List<JSONNode> cheapPrices = catalog.toStream()
                .filter(n -> n.getPath().endsWith("/price"))
                .filter(n -> Double.parseDouble(n.getValue().toString()) < 10.0)
                .collect(Collectors.toList());

        assertEquals(8, cheapPrices.size());
        assertTrue(cheapPrices.stream()
                .allMatch(n -> Double.parseDouble(n.getValue().toString()) < 10.0));
    }
}
