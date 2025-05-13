package org.json.junit.milestone3.tests;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.json.XML;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;

public class XMLKeyTransformerTest {
    // define some customized functions for testing

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


