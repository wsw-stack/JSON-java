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
    public void keyTransformerSimpleTest() {
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

/*
    @Test
    public void testXML01() {
        String xml = "<book><title><content>Old Title</content></title><author>John</author></book>";
        StringReader reader = new StringReader(xml);

        JSONObject result = XML.toJSONObject(reader, "swe_262p");

        System.out.println(result);
    }

    @Test
    public void testXML02() {
        String xmlString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
                "<contact>\n"+
                "  <nick>Crista </nick>\n"+
                "  <name>Crista Lopes</name>\n" +
                "  <address>\n" +
                "    <street>Ave of Nowhere</street>\n" +
                "    <zipcode>92614</zipcode>\n" +
                "  </address>\n" +
                "</contact>";

        try {
            JSONObject jobj = XML.toJSONObject(new StringReader(xmlString), "swe_262p");
            System.out.println(jobj);
        } catch (JSONException e) {
            System.out.println(e);
        }
    }
}
*/

