package org.json.junit.milestone5;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.junit.Test;

import java.io.Reader;
import java.io.StringReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class JSONObjectAsyncTest {

    private final XML.AsyncRunner runner = new XML.AsyncRunner();

    public JSONObjectAsyncTest() {
        // Default constructor
    }

    private final Reader medReader = new StringReader(
            "<catalog>"
                    + "  <book id=\"bk101\">"
                    + "    <author>Gambardella, Matthew</author>"
                    + "    <title>XML Developer's Guide</title>"
                    + "    <genre>Computer</genre>"
                    + "    <price>44.95</price>"
                    + "    <publish_date>2000-10-01</publish_date>"
                    + "    <description>An in-depth look at creating applications with XML.</description>"
                    + "  </book>"
                    + "  <book id=\"bk102\">"
                    + "    <author>Ralls, Kim</author>"
                    + "    <title>Midnight Rain</title>"
                    + "    <genre>Fantasy</genre>"
                    + "    <price>5.95</price>"
                    + "    <publish_date>2000-12-16</publish_date>"
                    + "    <description>A former architect battles corporate zombies, an evil sorceress, and her own childhood to become queen of the world.</description>"
                    + "  </book>"
                    + "</catalog>"
    );

    private final Reader smallReader = new StringReader(
            "<root>"
                    + "  <message>Hello</message>"
                    + "</root>"
    );

    @Test
    public void testAsyncParsing() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        JSONObject[] results = new JSONObject[2];
        Exception[] errors = new Exception[2];
        long[] timeElapsed = new long[2];

        final long startTime = System.nanoTime();

        Future<JSONObject> task1 = XML.toJSONObject(
                medReader,
                jo -> {
                    results[0] = jo;
                    long endNano = System.nanoTime();
                    timeElapsed[0] = TimeUnit.NANOSECONDS.toMillis(endNano - startTime);
                    latch.countDown();
                },
                e -> {
                    errors[0] = e;
                    latch.countDown();
                }
        );

        Future<JSONObject> task2 = XML.toJSONObject(
                smallReader,
                jo -> {
                    results[1] = jo;
                    long endNano = System.nanoTime();
                    timeElapsed[1] = TimeUnit.NANOSECONDS.toMillis(endNano - startTime);
                    latch.countDown();
                },
                e -> {
                    errors[1] = e;
                    latch.countDown();
                }
        );

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue("Both callbacks should complete within 10 seconds", completed);

        assertNull("No error expected for medReader", errors[0]);
        assertNull("No error expected for smallReader", errors[1]);

        JSONObject expectedMed = new JSONObject()
                .put("catalog", new JSONObject()
                        .put("book", new JSONArray()
                                .put(new JSONObject()
                                        .put("author", "Gambardella, Matthew")
                                        .put("title", "XML Developer's Guide")
                                        .put("genre", "Computer")
                                        .put("price", 44.95)
                                        .put("publish_date", "2000-10-01")
                                        .put("description", "An in-depth look at creating applications with XML.")
                                        .put("id", "bk101")
                                )
                                .put(new JSONObject()
                                        .put("author", "Ralls, Kim")
                                        .put("title", "Midnight Rain")
                                        .put("genre", "Fantasy")
                                        .put("price", 5.95)
                                        .put("publish_date", "2000-12-16")
                                        .put("description", "A former architect battles corporate zombies, an evil sorceress, and her own childhood to become queen of the world.")
                                        .put("id", "bk102")
                                )
                        )
                );

        JSONObject expectedSmall = new JSONObject()
                .put("root", new JSONObject()
                        .put("message", "Hello")
                );

        assertTrue("medReader JSON should match expected", expectedMed.similar(results[0]));
        assertTrue("smallReader JSON should match expected", expectedSmall.similar(results[1]));

        assertTrue("smallReader must be faster than medReader", timeElapsed[1] < timeElapsed[0]);

        // Optional debug output
        System.out.println("medReader elapsed: " + timeElapsed[0] + " ms");
        System.out.println("smallReader elapsed: " + timeElapsed[1] + " ms");
    }

    @Test
    public void testAsyncParsingWithInvalidXML() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Exception[] errors = new Exception[1];

        Reader invalidReader = new StringReader(
                "<root><tag>Unclosed tag</root>"
        );

        Future<JSONObject> task = org.json.XML.toJSONObject(
                invalidReader,
                jo -> fail("Should not succeed with invalid XML"),
                e -> {
                    errors[0] = e;
                    latch.countDown();
                }
        );

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Callback should complete within 5 seconds", completed);
        assertNotNull("Error should be captured for invalid XML", errors[0]);
        assertTrue("Error should be a JSONException", errors[0] instanceof org.json.JSONException);
    }

}
