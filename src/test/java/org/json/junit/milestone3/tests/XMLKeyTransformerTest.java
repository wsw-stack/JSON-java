package org.json.junit.milestone3.tests;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.json.XML;
import org.junit.Test;

import java.io.StringReader;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class XMLKeyTransformerTest {
    // define some customized functions for testing
    @Test
    public void keyTransformerAddPrefixTest() {
        String xml = "<book><title>Title</title><author>John</author></book>";
        Function<String, String> prefixer = key -> "swe262_" + key;

        JSONObject result = XML.toJSONObject(new StringReader(xml), prefixer);

        assertEquals("Title", result.getJSONObject("swe262_book").get("swe262_title"));
        assertEquals("John", result.getJSONObject("swe262_book").get("swe262_author"));
    }

    @Test
    public void keyTransformerReverseTest() {
        String xml = "<data><item>value</item></data>";
        Function<String, String> reverser = key -> new StringBuilder(key).reverse().toString();

        JSONObject result = XML.toJSONObject(new StringReader(xml), reverser);

        assertEquals("value", result.getJSONObject("atad").get("meti"));
    }

    @Test(expected = NullPointerException.class)
    public void nullReaderTest() {
        XML.toJSONObject(null, key -> "x_" + key);
    }
}
