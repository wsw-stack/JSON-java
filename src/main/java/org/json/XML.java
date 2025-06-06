package org.json;

/*
Public Domain.
*/

import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.io.BufferedReader;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This provides static methods to convert an XML text into a JSONObject, and to
 * covert a JSONObject into an XML text.
 *
 * @author JSON.org
 * @version 2016-08-10
 */
@SuppressWarnings("boxing")
public class XML {

    /**
     * Constructs a new XML object.
     */
    public XML() {
    }

    /** The Character '&amp;'. */
    public static final Character AMP = '&';

    /** The Character '''. */
    public static final Character APOS = '\'';

    /** The Character '!'. */
    public static final Character BANG = '!';

    /** The Character '='. */
    public static final Character EQ = '=';

    /** The Character <pre>{@code '>'. }</pre>*/
    public static final Character GT = '>';

    /** The Character '&lt;'. */
    public static final Character LT = '<';

    /** The Character '?'. */
    public static final Character QUEST = '?';

    /** The Character '"'. */
    public static final Character QUOT = '"';

    /** The Character '/'. */
    public static final Character SLASH = '/';

    /**
     * Null attribute name
     */
    public static final String NULL_ATTR = "xsi:nil";

    /**
     * Represents the XML attribute name for specifying type information.
     */
    public static final String TYPE_ATTR = "xsi:type";

    private static boolean replaced = false;
    private static boolean skipCurrentKey = false;


    /**
     * Creates an iterator for navigating Code Points in a string instead of
     * characters. Once Java7 support is dropped, this can be replaced with
     * <code>
     * string.codePoints()
     * </code>
     * which is available in Java8 and above.
     *
     * @see <a href=
     *      "http://stackoverflow.com/a/21791059/6030888">http://stackoverflow.com/a/21791059/6030888</a>
     */
    private static Iterable<Integer> codePointIterator(final String string) {
        return new Iterable<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {
                    private int nextIndex = 0;
                    private int length = string.length();

                    @Override
                    public boolean hasNext() {
                        return this.nextIndex < this.length;
                    }

                    @Override
                    public Integer next() {
                        int result = string.codePointAt(this.nextIndex);
                        this.nextIndex += Character.charCount(result);
                        return result;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    /**
     * Replace special characters with XML escapes:
     *
     * <pre>{@code
     * &amp; (ampersand) is replaced by &amp;amp;
     * &lt; (less than) is replaced by &amp;lt;
     * &gt; (greater than) is replaced by &amp;gt;
     * &quot; (double quote) is replaced by &amp;quot;
     * &apos; (single quote / apostrophe) is replaced by &amp;apos;
     * }</pre>
     *
     * @param string
     *            The string to be escaped.
     * @return The escaped string.
     */
    public static String escape(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (final int cp : codePointIterator(string)) {
            switch (cp) {
            case '&':
                sb.append("&amp;");
                break;
            case '<':
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '"':
                sb.append("&quot;");
                break;
            case '\'':
                sb.append("&apos;");
                break;
            default:
                if (mustEscape(cp)) {
                    sb.append("&#x");
                    sb.append(Integer.toHexString(cp));
                    sb.append(';');
                } else {
                    sb.appendCodePoint(cp);
                }
            }
        }
        return sb.toString();
    }

    /**
     * @param cp code point to test
     * @return true if the code point is not valid for an XML
     */
    private static boolean mustEscape(int cp) {
        /* Valid range from https://www.w3.org/TR/REC-xml/#charsets
         *
         * #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
         *
         * any Unicode character, excluding the surrogate blocks, FFFE, and FFFF.
         */
        // isISOControl is true when (cp >= 0 && cp <= 0x1F) || (cp >= 0x7F && cp <= 0x9F)
        // all ISO control characters are out of range except tabs and new lines
        return (Character.isISOControl(cp)
                && cp != 0x9
                && cp != 0xA
                && cp != 0xD
            ) || !(
                // valid the range of acceptable characters that aren't control
                (cp >= 0x20 && cp <= 0xD7FF)
                || (cp >= 0xE000 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0x10FFFF)
            )
        ;
    }

    /**
     * Removes XML escapes from the string.
     *
     * @param string
     *            string to remove escapes from
     * @return string with converted entities
     */
    public static String unescape(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (int i = 0, length = string.length(); i < length; i++) {
            char c = string.charAt(i);
            if (c == '&') {
                final int semic = string.indexOf(';', i);
                if (semic > i) {
                    final String entity = string.substring(i + 1, semic);
                    sb.append(XMLTokener.unescapeEntity(entity));
                    // skip past the entity we just parsed.
                    i += entity.length() + 1;
                } else {
                    // this shouldn't happen in most cases since the parser
                    // errors on unclosed entries.
                    sb.append(c);
                }
            } else {
                // not part of an entity
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Throw an exception if the string contains whitespace. Whitespace is not
     * allowed in tagNames and attributes.
     *
     * @param string
     *            A string.
     * @throws JSONException Thrown if the string contains whitespace or is empty.
     */
    public static void noSpace(String string) throws JSONException {
        int i, length = string.length();
        if (length == 0) {
            throw new JSONException("Empty string.");
        }
        for (i = 0; i < length; i += 1) {
            if (Character.isWhitespace(string.charAt(i))) {
                throw new JSONException("'" + string
                        + "' contains a space character.");
            }
        }
    }

    /**
     * Scan the content following the named tag, attaching it to the context.
     *
     * @param x
     *            The XMLTokener containing the source string.
     * @param context
     *            The JSONObject that will include the new material.
     * @param name
     *            The tag name.
     * @param config
     *            The XML parser configuration.
     * @param currentNestingDepth
     *            The current nesting depth.
     * @return true if the close tag is processed.
     * @throws JSONException Thrown if any parsing error occurs.
     */
    private static boolean parse(XMLTokener x, JSONObject context, String name, XMLParserConfiguration config, int currentNestingDepth)
            throws JSONException {
        char c;
        int i;
        JSONObject jsonObject = null;
        String string;
        String tagName;
        Object token;
        XMLXsiTypeConverter<?> xmlXsiTypeConverter;

        // Test for and skip past these forms:
        // <!-- ... -->
        // <! ... >
        // <![ ... ]]>
        // <? ... ?>
        // Report errors for these forms:
        // <>
        // <=
        // <<

        token = x.nextToken();

        // <!

        if (token == BANG) {
            c = x.next();
            if (c == '-') {
                if (x.next() == '-') {
                    x.skipPast("-->");
                    return false;
                }
                x.back();
            } else if (c == '[') {
                token = x.nextToken();
                if ("CDATA".equals(token)) {
                    if (x.next() == '[') {
                        string = x.nextCDATA();
                        if (string.length() > 0) {
                            context.accumulate(config.getcDataTagName(), string);
                        }
                        return false;
                    }
                }
                throw x.syntaxError("Expected 'CDATA['");
            }
            i = 1;
            do {
                token = x.nextMeta();
                if (token == null) {
                    throw x.syntaxError("Missing '>' after '<!'.");
                } else if (token == LT) {
                    i += 1;
                } else if (token == GT) {
                    i -= 1;
                }
            } while (i > 0);
            return false;
        } else if (token == QUEST) {

            // <?
            x.skipPast("?>");
            return false;
        } else if (token == SLASH) {

            // Close tag </

            token = x.nextToken();
            if (name == null) {
                throw x.syntaxError("Mismatched close tag " + token);
            }
            if (!token.equals(name)) {
                throw x.syntaxError("Mismatched " + name + " and " + token);
            }
            if (x.nextToken() != GT) {
                throw x.syntaxError("Misshaped close tag");
            }
            return true;

        } else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");

            // Open tag <

        } else {
            tagName = (String) token;
            token = null;
            jsonObject = new JSONObject();
            boolean nilAttributeFound = false;
            xmlXsiTypeConverter = null;
            for (;;) {
                if (token == null) {
                    token = x.nextToken();
                }
                // attribute = value
                if (token instanceof String) {
                    string = (String) token;
                    token = x.nextToken();
                    if (token == EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw x.syntaxError("Missing value");
                        }

                        if (config.isConvertNilAttributeToNull()
                                && NULL_ATTR.equals(string)
                                && Boolean.parseBoolean((String) token)) {
                            nilAttributeFound = true;
                        } else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
                                && TYPE_ATTR.equals(string)) {
                            xmlXsiTypeConverter = config.getXsiTypeMap().get(token);
                        } else if (!nilAttributeFound) {
                            Object obj = stringToValue((String) token);
                            if (obj instanceof Boolean) {
                                jsonObject.accumulate(string,
                                        config.isKeepBooleanAsString()
                                                ? ((String) token)
                                                : obj);
                            } else if (obj instanceof Number) {
                                jsonObject.accumulate(string,
                                        config.isKeepNumberAsString()
                                                ? ((String) token)
                                                : obj);
                            } else {
                                jsonObject.accumulate(string, stringToValue((String) token));
                            }
                        }
                        token = null;
                    } else {
                        jsonObject.accumulate(string, "");
                    }


                } else if (token == SLASH) {
                    // Empty tag <.../>
                    if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (config.getForceList().contains(tagName)) {
                        // Force the value to be an array
                        if (nilAttributeFound) {
                            context.append(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.append(tagName, jsonObject);
                        } else {
                            context.put(tagName, new JSONArray());
                        }
                    } else {
                        if (nilAttributeFound) {
                            context.accumulate(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.accumulate(tagName, jsonObject);
                        } else {
                            context.accumulate(tagName, "");
                        }
                    }
                    return false;

                } else if (token == GT) {
                    // Content, between <...> and </...>
                    for (;;) {
                        token = x.nextContent();
                        if (token == null) {
                            if (tagName != null) {
                                throw x.syntaxError("Unclosed tag " + tagName);
                            }
                            return false;
                        } else if (token instanceof String) {
                            string = (String) token;
                            if (string.length() > 0) {
                                if(xmlXsiTypeConverter != null) {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                            stringToValue(string, xmlXsiTypeConverter));
                                } else {
                                    Object obj = stringToValue((String) token);
                                    if (obj instanceof Boolean) {
                                        jsonObject.accumulate(config.getcDataTagName(),
                                                config.isKeepBooleanAsString()
                                                        ? ((String) token)
                                                        : obj);
                                    } else if (obj instanceof Number) {
                                        jsonObject.accumulate(config.getcDataTagName(),
                                                config.isKeepNumberAsString()
                                                        ? ((String) token)
                                                        : obj);
                                    } else {
                                        jsonObject.accumulate(config.getcDataTagName(), stringToValue((String) token));
                                    }
                                }
                            }

                        } else if (token == LT) {
                            // Nested element
                            if (currentNestingDepth == config.getMaxNestingDepth()) {
                                throw x.syntaxError("Maximum nesting depth of " + config.getMaxNestingDepth() + " reached");
                            }

                            if (parse(x, jsonObject, tagName, config, currentNestingDepth + 1)) {
                                if (config.getForceList().contains(tagName)) {
                                    // Force the value to be an array
                                    if (jsonObject.length() == 0) {
                                        context.put(tagName, new JSONArray());
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(config.getcDataTagName()) != null) {
                                        context.append(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        context.append(tagName, jsonObject);
                                    }
                                } else {
                                    if (jsonObject.length() == 0) {
                                        context.accumulate(tagName, "");
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(config.getcDataTagName()) != null) {
                                        context.accumulate(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        if (!config.shouldTrimWhiteSpace()) {
                                            removeEmpty(jsonObject, config);
                                        }
                                        context.accumulate(tagName, jsonObject);
                                    }
                                }

                                return false;
                            }
                        }
                    }
                } else {
                    throw x.syntaxError("Misshaped tag");
                }
            }
        }
    }

    /**
     * Compared to the original parse function, this function adds the function (String Convertor) as an input
     * @param x
     * @param context
     * @param name
     * @param config
     * @param currentNestingDepth
     * @param keyTransformer
     *      The function which takes in a single String parameter, and returns another converted String
     * @return
     * @throws JSONException
     */
    private static boolean parseMilestone3(XMLTokener x, JSONObject context, String name, XMLParserConfiguration config, int currentNestingDepth,Function<String, String> keyTransformer)
            throws JSONException {
        char c;
        int i;
        JSONObject jsonObject = null;
        String string;
        String tagName;
        Object token;
        XMLXsiTypeConverter<?> xmlXsiTypeConverter;

        // Test for and skip past these forms:
        // <!-- ... -->
        // <! ... >
        // <![ ... ]]>
        // <? ... ?>
        // Report errors for these forms:
        // <>
        // <=
        // <<
        token = x.nextToken();

        // <!
        if (token == BANG) {
            c = x.next();
            if (c == '-') {
                if (x.next() == '-') {
                    x.skipPast("-->");
                    return false;
                }
                x.back();
            } else if (c == '[') {
                token = x.nextToken();
                if ("CDATA".equals(token)) {
                    if (x.next() == '[') {
                        string = x.nextCDATA();
                        if (string.length() > 0) {
                            context.accumulate(config.getcDataTagName(), string);
                        }
                        return false;
                    }
                }
                throw x.syntaxError("Expected 'CDATA['");
            }
            i = 1;
            do {
                token = x.nextMeta();
                if (token == null) {
                    throw x.syntaxError("Missing '>' after '<!'.");
                } else if (token == LT) {
                    i += 1;
                } else if (token == GT) {
                    i -= 1;
                }
            } while (i > 0);
            return false;
        } else if (token == QUEST) {

            // <?
            x.skipPast("?>");
            return false;
        } else if (token == SLASH) {

            // Close tag </

            token = x.nextToken();
            if (name == null) {
                throw x.syntaxError("Mismatched close tag " + token);
            }
            if (!token.equals(name)) {
                throw x.syntaxError("Mismatched " + name + " and " + token);
            }
            if (x.nextToken() != GT) {
                throw x.syntaxError("Misshaped close tag");
            }
            return true;

        } else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");

            // Open tag <

        } else {
            tagName = (String) token;
            String transformedTagName = keyTransformer.apply(tagName);//add
            token = null;
            jsonObject = new JSONObject();
            boolean nilAttributeFound = false;
            xmlXsiTypeConverter = null;
            for (;;) {
                if (token == null) {
                    token = x.nextToken();
                }
                // attribute = value
                if (token instanceof String) {
                    string = (String) token;
                    token = x.nextToken();
                    if (token == EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw x.syntaxError("Missing value");
                        }
                        String transformedKey = keyTransformer.apply(string); //add new code
                        if (config.isConvertNilAttributeToNull()
                                && NULL_ATTR.equals(string)
                                && Boolean.parseBoolean((String) token)) {
                            nilAttributeFound = true;
                        } else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
                                && TYPE_ATTR.equals(string)) {
                            xmlXsiTypeConverter = config.getXsiTypeMap().get(token);
                        } else if (!nilAttributeFound) {
                            Object obj = stringToValue((String) token);
                            jsonObject.accumulate(transformedKey, obj);
                        }
                        token = null;
                    } else {
                        jsonObject.accumulate(keyTransformer.apply(string), "");
                    }
                } else if (token == SLASH) {
                    // Empty tag <.../>
                    if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (config.getForceList().contains(tagName)) {
                        if (nilAttributeFound) {
                            context.append(transformedTagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.append(transformedTagName, jsonObject);
                        } else {
                            context.put(transformedTagName, new JSONArray());
                        }
                    } else {
                        if (nilAttributeFound) {
                            context.accumulate(transformedTagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.accumulate(transformedTagName, jsonObject);
                        } else {
                            context.accumulate(transformedTagName, "");
                        }
                    }
                    return false;
                    /*
                    if (config.getForceList().contains(prefix + tagName)) {
                        // Force the value to be an array
                        if (nilAttributeFound) {
                            context.append(prefix + tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.append(prefix + tagName, jsonObject);
                        } else {
                            context.put(prefix + tagName, new JSONArray());
                        }
                    } else {
                        if (nilAttributeFound) {
                            context.accumulate(prefix + tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.accumulate(prefix + tagName, jsonObject);
                        } else {
                            context.accumulate(prefix + tagName, "");
                        }
                    }
                    return false;
                    */
                } else if (token == GT) {
                    // Content, between <...> and </...>
                    for (;;) {
                        token = x.nextContent();
                        if (token == null) {
                            if (tagName != null) {
                                throw x.syntaxError("Unclosed tag " + tagName);
                            }
                            return false;
                        } else if (token instanceof String) {
                            string = (String) token;
                            if (string.length() > 0) {
                                if(xmlXsiTypeConverter != null) {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                            stringToValue(string, xmlXsiTypeConverter));
                                } else {
                                    Object obj = stringToValue((String) token);
                                    if (obj instanceof Boolean) {
                                        jsonObject.accumulate(config.getcDataTagName(),
                                                config.isKeepBooleanAsString()
                                                        ? ((String) token)
                                                        : obj);
                                    } else if (obj instanceof Number) {
                                        jsonObject.accumulate(config.getcDataTagName(),
                                                config.isKeepNumberAsString()
                                                        ? ((String) token)
                                                        : obj);
                                    } else {
                                        jsonObject.accumulate(config.getcDataTagName(), stringToValue((String) token));
                                    }
                                }
                            }

                        } else if (token == LT) {
                            if (parseMilestone3(x, jsonObject,tagName, config, currentNestingDepth + 1, keyTransformer)) {
                                if (config.getForceList().contains(tagName)) {
                                    if (jsonObject.length() == 0) {
                                        context.put(transformedTagName, new JSONArray());
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(config.getcDataTagName()) != null) {
                                        context.append(transformedTagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        context.append(transformedTagName, jsonObject);
                                    }
                                } else {
                                    if (jsonObject.length() == 0) {
                                        context.accumulate(transformedTagName, "");
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(config.getcDataTagName()) != null) {
                                        context.accumulate(transformedTagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        context.accumulate(transformedTagName, jsonObject);
                                    }
                                }
                                return false;
                            }
                        }
                    }
                } else {
                    throw x.syntaxError("Misshaped tag");
                }
            }
        }
    }
    /**
     * This method removes any JSON entry which has the key set by XMLParserConfiguration.cDataTagName
     * and contains whitespace as this is caused by whitespace between tags. See test XMLTest.testNestedWithWhitespaceTrimmingDisabled.
     * @param jsonObject JSONObject which may require deletion
     * @param config The XMLParserConfiguration which includes the cDataTagName
     */
    private static void removeEmpty(final JSONObject jsonObject, final XMLParserConfiguration config) {
        if (jsonObject.has(config.getcDataTagName()))  {
            final Object s = jsonObject.get(config.getcDataTagName());
            if (s instanceof String) {
                if (isStringAllWhiteSpace(s.toString())) {
                    jsonObject.remove(config.getcDataTagName());
                }
            }
            else if (s instanceof JSONArray) {
                final JSONArray sArray = (JSONArray) s;
                for (int k = sArray.length()-1; k >= 0; k--){
                    final Object eachString = sArray.get(k);
                    if (eachString instanceof String) {
                        String s1 = (String) eachString;
                        if (isStringAllWhiteSpace(s1)) {
                            sArray.remove(k);
                        }
                    }
                }
                if (sArray.isEmpty()) {
                    jsonObject.remove(config.getcDataTagName());
                }
            }
        }
    }

    private static boolean isStringAllWhiteSpace(final String s) {
        for (int k = 0; k<s.length(); k++){
            final char eachChar = s.charAt(k);
            if (!Character.isWhitespace(eachChar)) {
                return false;
            }
        }
        return true;
    }

    /**
     * direct copy of {@link JSONObject#stringToNumber(String)} to maintain Android support.
     */
    private static Number stringToNumber(final String val) throws NumberFormatException {
        char initial = val.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            // decimal representation
            if (isDecimalNotation(val)) {
                // Use a BigDecimal all the time so we keep the original
                // representation. BigDecimal doesn't support -0.0, ensure we
                // keep that by forcing a decimal.
                try {
                    BigDecimal bd = new BigDecimal(val);
                    if(initial == '-' && BigDecimal.ZERO.compareTo(bd)==0) {
                        return Double.valueOf(-0.0);
                    }
                    return bd;
                } catch (NumberFormatException retryAsDouble) {
                    // this is to support "Hex Floats" like this: 0x1.0P-1074
                    try {
                        Double d = Double.valueOf(val);
                        if(d.isNaN() || d.isInfinite()) {
                            throw new NumberFormatException("val ["+val+"] is not a valid number.");
                        }
                        return d;
                    } catch (NumberFormatException ignore) {
                        throw new NumberFormatException("val ["+val+"] is not a valid number.");
                    }
                }
            }
            // block items like 00 01 etc. Java number parsers treat these as Octal.
            if(initial == '0' && val.length() > 1) {
                char at1 = val.charAt(1);
                if(at1 >= '0' && at1 <= '9') {
                    throw new NumberFormatException("val ["+val+"] is not a valid number.");
                }
            } else if (initial == '-' && val.length() > 2) {
                char at1 = val.charAt(1);
                char at2 = val.charAt(2);
                if(at1 == '0' && at2 >= '0' && at2 <= '9') {
                    throw new NumberFormatException("val ["+val+"] is not a valid number.");
                }
            }
            // integer representation.
            // This will narrow any values to the smallest reasonable Object representation
            // (Integer, Long, or BigInteger)

            // BigInteger down conversion: We use a similar bitLength compare as
            // BigInteger#intValueExact uses. Increases GC, but objects hold
            // only what they need. i.e. Less runtime overhead if the value is
            // long lived.
            BigInteger bi = new BigInteger(val);
            if(bi.bitLength() <= 31){
                return Integer.valueOf(bi.intValue());
            }
            if(bi.bitLength() <= 63){
                return Long.valueOf(bi.longValue());
            }
            return bi;
        }
        throw new NumberFormatException("val ["+val+"] is not a valid number.");
    }

    /**
     * direct copy of {@link JSONObject#isDecimalNotation(String)} to maintain Android support.
     */
    private static boolean isDecimalNotation(final String val) {
        return val.indexOf('.') > -1 || val.indexOf('e') > -1
                || val.indexOf('E') > -1 || "-0".equals(val);
    }

    /**
     * This method tries to convert the given string value to the target object
     * @param string String to convert
     * @param typeConverter value converter to convert string to integer, boolean e.t.c
     * @return JSON value of this string or the string
     */
    public static Object stringToValue(String string, XMLXsiTypeConverter<?> typeConverter) {
        if(typeConverter != null) {
            return typeConverter.convert(string);
        }
        return stringToValue(string);
    }

    /**
     * This method is the same as {@link JSONObject#stringToValue(String)}.
     *
     * @param string String to convert
     * @return JSON value of this string or the string
     */
    // To maintain compatibility with the Android API, this method is a direct copy of
    // the one in JSONObject. Changes made here should be reflected there.
    // This method should not make calls out of the XML object.
    public static Object stringToValue(String string) {
        if ("".equals(string)) {
            return string;
        }

        // check JSON key words true/false/null
        if ("true".equalsIgnoreCase(string)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(string)) {
            return Boolean.FALSE;
        }
        if ("null".equalsIgnoreCase(string)) {
            return JSONObject.NULL;
        }

        /*
         * If it might be a number, try converting it. If a number cannot be
         * produced, then the value will just be a string.
         */

        char initial = string.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            try {
                return stringToNumber(string);
            } catch (Exception ignore) {
            }
        }
        return string;
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * @param string
     *            The source string.
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string) throws JSONException {
        return toJSONObject(string, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * @param reader The XML source reader.
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader) throws JSONException {
        return toJSONObject(reader, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param reader The XML source reader.
     * @param keepStrings If true, then values will not be coerced into boolean
     *  or numeric values and will instead be left as strings
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader, boolean keepStrings) throws JSONException {
        if(keepStrings) {
            return toJSONObject(reader, XMLParserConfiguration.KEEP_STRINGS);
        }
        return toJSONObject(reader, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All numbers are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document depending
     * on how flag is set.
     * All booleans are converted as strings, for true, false will not be coerced to
     * booleans but will instead be the exact value as seen in the XML document depending
     * on how flag is set.
     *
     * @param reader The XML source reader.
     * @param keepNumberAsString If true, then numeric values will not be coerced into
     *  numeric values and will instead be left as strings
     * @param keepBooleanAsString If true, then boolean values will not be coerced into
     *      *  numeric values and will instead be left as strings
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader, boolean keepNumberAsString, boolean keepBooleanAsString) throws JSONException {
        XMLParserConfiguration xmlParserConfiguration = new XMLParserConfiguration();
        if(keepNumberAsString) {
            xmlParserConfiguration = xmlParserConfiguration.withKeepNumberAsString(keepNumberAsString);
        }
        if(keepBooleanAsString) {
            xmlParserConfiguration = xmlParserConfiguration.withKeepBooleanAsString(keepBooleanAsString);
        }
        return toJSONObject(reader, xmlParserConfiguration);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param reader The XML source reader.
     * @param config Configuration options for the parser
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader, XMLParserConfiguration config) throws JSONException {
        JSONObject jo = new JSONObject();
        XMLTokener x = new XMLTokener(reader, config);
        while (x.more()) {
            x.skipPast("<");
            if(x.more()) {
                parse(x, jo, null, config, 0);
            }
        }
        return jo;
    }

    /**
     *
     * @param reader, a reader with XML content inside
     * @param path, a Json path for querying the inside object
     * @return a Json object which matches the given JsonPointer path, or throw an error if not found
     * @throws JSONException
     */
    public static JSONObject toJSONObject(Reader reader, JSONPointer path) throws JSONException {
        XMLTokener x = new XMLTokener(reader);
        // parse the JSONPointer
        String pointerExpr = path.toString();
        List<Object> tokens = new ArrayList<>();
        if (!pointerExpr.isEmpty()) {
            String[] parts = pointerExpr.split("/", -1);
            for (int i = (pointerExpr.startsWith("/") ? 1 : 0); i < parts.length; i++) {
                String part = parts[i].replace("~1", "/").replace("~0", "~");
                if (part.isEmpty()) {
                    tokens.add("");
                } else if (part.matches("-?\\d+") && !(part.startsWith("0") && part.length() > 1)) {
                    try {
                        int index = Integer.parseInt(part);
                        if (index < 0) {
                            tokens.add(part);
                        } else {
                            tokens.add(index);
                            continue;
                        }
                    } catch (NumberFormatException e) {
                    }
                }
                tokens.add(part);
            }
        }
        // pointer is empty, then parse the whole document
        if (tokens.isEmpty()) {
            return XML.toJSONObject(reader);
        }

        JSONObject result = null;
        Object firstToken = tokens.get(0);
        if (!(firstToken instanceof String)) {
            throw new JSONException("Path not found: " + path);
        }
        String targetRoot = (String) firstToken;
        Integer targetRootIndex = null;
        int nextTokenIndex = 1;
        if (nextTokenIndex < tokens.size() && tokens.get(nextTokenIndex) instanceof Integer) {
            targetRootIndex = (Integer) tokens.get(nextTokenIndex);
            nextTokenIndex++;
        }
        int currentIndexCount = 0;

        while (x.more()) {
            x.skipPast("<");
            if (!x.more()) break;
            char c = x.next();
            if (c == '?') {
                // XML announcement
                x.skipPast("?>");
                continue;
            }
            if (c == '!') {
                if (x.more()) {
                    char c2 = x.next();
                    if (c2 == '-' && x.more() && x.next() == '-') {
                        x.skipPast("-->");
                    } else if (c2 == '[') {
                        x.skipPast("]]>");
                    } else {
                        // skip <! announcement (can be comments)
                        x.skipPast(">");
                    }
                }
                continue;
            }
            if (c == '/') {
                // unexpected closing tag
                continue;
            }
            x.back();
            Object token = x.nextToken();
            if (!(token instanceof String)) {
                throw x.syntaxError("Misshaped element");
            }
            String tagName = (String) token;
            // if the top level matches
            if (tagName.equals(targetRoot)) {
                // get the index
                if (targetRootIndex != null) {
                    if (currentIndexCount < targetRootIndex) {
                        // skip the whole tree if not reached yet
                        skipElement(x, tagName);
                        currentIndexCount++;
                        continue;
                    } else if (currentIndexCount > targetRootIndex) {
                        break;
                    }
                }
                currentIndexCount++;
                // path only contains root element itself
                if (nextTokenIndex >= tokens.size()) {
                    // parse the whole root element as JSONObject
                    result = parseElement(x, tagName);
                    break;
                }
                boolean selfClosing = false;
                JSONObject currentObj = new JSONObject();
                while (true) {
                    token = x.nextToken();
                    if (token == null) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (token instanceof Character) {
                        char ch = (Character) token;
                        if (ch == '>') {
                            break;
                        }
                        if (ch == '/') {
                            // end of element
                            if (x.next() != '>') {
                                throw x.syntaxError("Misshaped tag");
                            }
                            selfClosing = true;
                            break;
                        }
                    } else {
                        String attrName = (String) token;
                        Object nextTok = x.nextToken();
                        if (nextTok == XML.EQ) {
                            Object attrValueToken = x.nextToken();
                            if (!(attrValueToken instanceof String)) {
                                throw x.syntaxError("Missing value for attribute " + attrName);
                            }
                            String attrValue = (String) attrValueToken;
                            currentObj.accumulate(attrName, XML.stringToValue(attrValue));
                        } else {
                            currentObj.accumulate(attrName, "");
                            token = nextTok;
                            if (token instanceof Character) {
                                x.back();
                            }
                        }
                    }
                }
                if (selfClosing) {
                    // if tag is empty and path does not end here
                    result = null;
                } else {
                    result = findInElement(x, tagName, nextTokenIndex, tokens);
                }
                // if result already found
                if (result != null) {
                    break;
                } else {
                    // continue searching for the element
                    continue;
                }
            } else {
                // if the top level does not match
                skipElement(x, tagName);
            }
        }
        if (result == null) {
            throw new JSONException("Path not found: " + path.toString());
        }
        return result;
    }

    /**
     * Given a customized function, convert the keys in the Json Object
     * @param reader the XML input
     * @param keyTransformer a function that transforms each key name
     * @return JSONObject with transformed keys
     * @throws JSONException if any XML parsing or transformation fails
     */
    public static JSONObject toJSONObject(Reader reader, Function<String, String> keyTransformer) throws JSONException {
        JSONObject result = new JSONObject();
        XMLTokener x = new XMLTokener(reader);

        while (x.more()) {
            x.skipPast("<");
            if (x.more()) {
                XML.parseMilestone3(x, result, null, XMLParserConfiguration.ORIGINAL, 0, keyTransformer);
            }
        }
        return result;
    }
    /*
    public static JSONObject toJSONObject(Reader reader, String prefix) throws JSONException {
        JSONObject jo = new JSONObject();
        XMLParserConfiguration config = XMLParserConfiguration.ORIGINAL;
        XMLTokener x = new XMLTokener(reader, config);
        while (x.more()) {
            x.skipPast("<");
            if(x.more()) {
                parse(x, jo, null, prefix, config, 0);
            }
        }
        return jo;
    }
*/
    /**
     * Helper method: skip the current element and its entire subtree without
     * building any JSON output.
     *
     * Preconditions:
     *   The caller has already read the element name (we are positioned
     *     right after the `<name` token).
     *   The tokenizer cursor is at the first token after the element name.
     */
    private static void skipElement(XMLTokener x, String tagName) throws JSONException {
        // Consume attributes until we hit the end of the start‑tag
        Object token;
        boolean selfClosing = false;
        while ((token = x.nextToken()) != null) {
            if (token instanceof Character) {
                char ch = (Character) token;
                if (ch == '>') {           // normal end of start‑tag
                    break;
                }
                if (ch == '/') {           // empty‑element tag `/>`
                    if (x.next() != '>') {
                        throw x.syntaxError("Misshaped tag");
                    }
                    selfClosing = true;
                    break;
                }
            }
            // Otherwise ‑– attribute name or value, ignore
        }
        if (!selfClosing) {
            // Skip everything until we see the matching close tag
            int depth = 0;
            while (true) {
                x.skipPast("<");
                if (!x.more()) {
                    throw x.syntaxError("Unclosed tag " + tagName);
                }
                char c = x.next();
                if (c == '/') {
                    // Found a closing tag
                    Object nameToken = x.nextToken();
                    if (!(nameToken instanceof String)) {
                        throw x.syntaxError("Missing close name");
                    }
                    String closeName = (String) nameToken;
                    if (x.next() != '>') {
                        throw x.syntaxError("Misshaped close tag");
                    }
                    if (closeName.equals(tagName)) {
                        if (depth == 0) {
                            // Reached the matching close tag – done
                            break;
                        } else {
                            // Closing an inner tag with the same name
                            depth--;
                            continue;
                        }
                    } else {
                        // Closing some other tag – ignore
                        continue;
                    }
                } else if (c == '!') {
                    // Comment / CDATA / DOCTYPE – skip
                    if (x.more()) {
                        char c2 = x.next();
                        if (c2 == '-' && x.more() && x.next() == '-') {
                            x.skipPast("-->");
                        } else if (c2 == '[') {
                            x.skipPast("]]>");
                        } else {
                            x.skipPast(">");
                        }
                    }
                    continue;
                } else if (c == '?') {
                    // Processing instruction – skip
                    x.skipPast("?>");
                    continue;
                } else {
                    // New child element – recurse to skip it
                    x.back();
                    Object newName = x.nextToken();
                    if (!(newName instanceof String)) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    skipElement(x, (String) newName);
                    // If the child has the same tag name, track nesting depth
                    if (((String) newName).equals(tagName)) {
                        depth++;
                    }
                }
            }
        }
    }

    /**
     * Search within the current element for the sub‑path specified by `tokens`,
     * starting at `tokenIndex`.  Stops as soon as the desired node is found.
     *
     * @param x          XMLTokener – cursor is right after the parent start‑tag.
     * @param parentName The name of the element we are currently inside.
     * @param tokenIndex Index of the current JSONPointer token to match.
     * @param tokens     Full list of JSONPointer tokens.
     * @return           The matched JSONObject, or {@code null} if not found here.
     */
    private static JSONObject findInElement(XMLTokener x,
                                            String parentName,
                                            int tokenIndex,
                                            List<Object> tokens) throws JSONException {
        String  targetName  = null;   // child element name to look for
        Integer targetIndex = null;   // optional array index
        int     nextIndex   = tokenIndex;

        if (tokenIndex < tokens.size()) {
            Object tk = tokens.get(tokenIndex);
            if (tk instanceof String) {
                targetName = (String) tk;
                if (tokenIndex + 1 < tokens.size()
                        && tokens.get(tokenIndex + 1) instanceof Integer) {
                    targetIndex = (Integer) tokens.get(tokenIndex + 1);
                    nextIndex   = tokenIndex + 2;
                } else {
                    nextIndex   = tokenIndex + 1;
                }
            } else { // JSONPointer should not give a number at an object level
                return null;
            }
        }

        int       count  = 0;      // how many <targetName> siblings seen
        JSONObject result = null;

        while (true) {
            Object contentToken = x.nextContent();
            if (contentToken == null) {
                throw x.syntaxError("Unclosed tag " + parentName);
            }
            if (contentToken instanceof String) {
                // Ignore plain text (unless pointer explicitly targets "content")
                continue;
            }
            if (contentToken instanceof Character && (Character) contentToken == '<') {
                char c = x.next();
                if (c == '/') {              // end‑tag for parent
                    Object closeName = x.nextToken();
                    if (!(closeName instanceof String) || !closeName.equals(parentName)) {
                        throw x.syntaxError("Mismatched close tag for " + parentName);
                    }
                    if (x.next() != '>') {
                        throw x.syntaxError("Misshaped close tag");
                    }
                    break;                   // search in this parent finished
                }
                if (c == '?') { x.skipPast("?>"); continue; }
                if (c == '!') {
                    // comment / CDATA – skip
                    if (x.more()) {
                        char c2 = x.next();
                        if (c2 == '-' && x.more() && x.next() == '-') {
                            x.skipPast("-->");
                        } else if (c2 == '[') {
                            x.skipPast("]]>");
                        } else {
                            x.skipPast(">");
                        }
                    }
                    continue;
                }
                // Child element start
                x.back();
                Object childToken = x.nextToken();
                if (!(childToken instanceof String)) {
                    throw x.syntaxError("Bad tag syntax");
                }
                String childName = (String) childToken;

                if (targetName != null && childName.equals(targetName)) {
                    // Found desired child name
                    if (targetIndex != null) {          // need a specific index
                        if (count < targetIndex) {
                            skipElement(x, childName);  // not yet reached – skip
                            count++;
                            continue;
                        }
                        count++;                        // now at the right sibling
                    } else {                            // first match is enough
                        count++;
                        if (count > 1) {                // ambiguous path
                            skipElement(x, childName);
                            continue;
                        }
                    }

                    // Dive into this child
                    if (nextIndex >= tokens.size()) {
                        result = parseElement(x, childName);  // path ends here
                    } else {
                        result = findInElement(x, childName, nextIndex, tokens);
                    }
                    return result;  // regardless of success, stop searching siblings
                } else {
                    // Not the target child – skip whole subtree
                    skipElement(x, childName);
                }
            }
        }
        return null;  // target not found in this element
    }

    /**
     * Parse the current element (including its subtree) into a JSONObject.
     *
     * Preconditions:
     *   – Caller has already consumed the element name; tokenizer cursor is
     *     positioned immediately after that name token.
     */
    private static JSONObject parseElement(XMLTokener x, String tagName) throws JSONException {
        JSONObject jo = new JSONObject();
        Object token;
        boolean selfClosing = false;

        /* ---------- Parse attributes ---------- */
        while ((token = x.nextToken()) != null) {
            if (token instanceof Character) {
                char ch = (Character) token;
                if (ch == '>') { break; }                  // end of start‑tag
                if (ch == '/') {                           // empty element
                    if (x.next() != '>') {
                        throw x.syntaxError("Misshaped tag");
                    }
                    selfClosing = true;
                    break;
                }
            } else {
                String attrName = (String) token;
                Object nextTok  = x.nextToken();
                if (nextTok == XML.EQ) {
                    Object valTok = x.nextToken();
                    if (!(valTok instanceof String)) {
                        throw x.syntaxError("Missing value for attribute " + attrName);
                    }
                    jo.accumulate(attrName, XML.stringToValue((String) valTok));
                } else {
                    // Attribute without value
                    jo.accumulate(attrName, "");
                    if (nextTok instanceof Character) {
                        char ch2 = (Character) nextTok;
                        if (ch2 == '>') { break; }
                        if (ch2 == '/') {
                            if (x.next() != '>') throw x.syntaxError("Misshaped tag");
                            selfClosing = true;
                            break;
                        }
                    }
                    token = nextTok; // nextTok could be another attribute name
                    continue;
                }
            }
        }
        if (selfClosing) {
            return jo; // nothing more to parse
        }

        /* ---------- Parse children / text ---------- */
        StringBuilder textBuf = null;
        while (true) {
            Object contentToken = x.nextContent();
            if (contentToken == null) {
                throw x.syntaxError("Unclosed tag " + tagName);
            }
            if (contentToken instanceof String) {
                String txt = (String) contentToken;
                if (!txt.isEmpty()) {
                    if (textBuf == null) textBuf = new StringBuilder();
                    textBuf.append(XML.stringToValue(txt));
                }
            } else if (contentToken instanceof Character
                    && (Character) contentToken == '<') {
                char c = x.next();
                if (c == '/') {                          // end‑tag
                    Object closeTok = x.nextToken();
                    String closeName = (closeTok instanceof String) ? (String) closeTok : "";
                    if (!closeName.equals(tagName)) {
                        throw x.syntaxError("Mismatched close tag for " + tagName);
                    }
                    if (x.next() != '>') {
                        throw x.syntaxError("Misshaped close tag");
                    }
                    if (textBuf != null && textBuf.length() > 0) {
                        jo.accumulate("content", textBuf.toString());
                    }
                    return jo;
                }
                if (c == '?') { x.skipPast("?>"); continue; }
                if (c == '!') {
                    if (x.more()) {
                        char c2 = x.next();
                        if (c2 == '-' && x.more() && x.next() == '-') {
                            x.skipPast("-->");
                        } else if (c2 == '[') {
                            x.skipPast("]]>");
                        } else {
                            x.skipPast(">");
                        }
                    }
                    continue;
                }
                // Child element
                x.back();
                Object childNameTok = x.nextToken();
                if (!(childNameTok instanceof String)) {
                    throw x.syntaxError("Bad tag syntax");
                }
                String childName = (String) childNameTok;
                JSONObject childObj = parseElement(x, childName);

                // Merge child into current object (array‑if‑needed semantics)
                Object existing = jo.opt(childName);
                if (existing == null) {
                    jo.accumulate(childName, childObj.length() > 0 ? childObj : "");
                } else if (existing instanceof JSONArray) {
                    ((JSONArray) existing).put(childObj.length() > 0 ? childObj : "");
                } else {
                    JSONArray arr = new JSONArray();
                    arr.put(existing);
                    arr.put(childObj.length() > 0 ? childObj : "");
                    jo.put(childName, arr);
                }

                // Flush buffered text, if any
                if (textBuf != null && textBuf.length() > 0) {
                    jo.accumulate("content", textBuf.toString());
                    textBuf.setLength(0);
                }
            }
        }
    }

    /** SWE262P MileStone2 project, Task2 by Jiacheng Zhuo **/

    /** Edit the parse method, add functions for the replacement implement **/
    private static boolean parseMilestone2(XMLTokener x, JSONObject context, String name, XMLParserConfiguration config, int currentNestingDepth, List<String> targetPath,
                                           int targetPathLength,
                                           Map<String, Integer> arrayKey,
                                           boolean isReplace,
                                           boolean mergeToParent,
                                           JSONObject replacement)
            throws JSONException {
        char c;
        int i;
        JSONObject jsonObject = null;
        String string;
        String tagName;
        Object token;
        XMLXsiTypeConverter<?> xmlXsiTypeConverter;


        // Test for and skip past these forms:
        // <!-- ... -->
        // <! ... >
        // <![ ... ]]>
        // <? ... ?>
        // Report errors for these forms:
        // <>
        // <=
        // <<

        token = x.nextToken();

        // <!

        if (token == BANG) {
            c = x.next();
            if (c == '-') {
                if (x.next() == '-') {
                    x.skipPast("-->");
                    return false;
                }
                x.back();
            } else if (c == '[') {
                token = x.nextToken();
                if ("CDATA".equals(token)) {
                    if (x.next() == '[') {
                        string = x.nextCDATA();
                        if (string.length() > 0) {
                            context.accumulate(config.getcDataTagName(), string);
                        }
                        return false;
                    }
                }
                throw x.syntaxError("Expected 'CDATA['");
            }
            i = 1;
            do {
                token = x.nextMeta();
                if (token == null) {
                    throw x.syntaxError("Missing '>' after '<!'.");
                } else if (token == LT) {
                    i += 1;
                } else if (token == GT) {
                    i -= 1;
                }
            } while (i > 0);
            return false;
        } else if (token == QUEST) {

            // <?
            x.skipPast("?>");
            return false;
        } else if (token == SLASH) {

            // Close tag </

            token = x.nextToken();
            if (name == null) {
                throw x.syntaxError("Mismatched close tag " + token);
            }
            if (!token.equals(name)) {
                throw x.syntaxError("Mismatched " + name + " and " + token);
            }
            if (x.nextToken() != GT) {
                throw x.syntaxError("Misshaped close tag");
            }
            return true;

        } else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");

            // Open tag <

        } else {
            //--------add the replacement logic for new parse function by Jiacheng Zhuo----------------//
            String currentTag = token.toString();
            if (currentNestingDepth < targetPathLength) {
                boolean isTargetMatch = (currentNestingDepth == targetPathLength - 1) &&
                        targetPath.get(targetPathLength - 1).equals(currentTag);
                boolean hasIndex = arrayKey.containsKey(currentTag);//
                int remainingIndex = hasIndex ? arrayKey.get(currentTag) : 0;//
                boolean indexMatches = !hasIndex || remainingIndex == 0; //

                if (isReplace && !replaced && isTargetMatch && indexMatches) {
                    if (isReplace && !replaced && isTargetMatch && indexMatches) {
                        context.put(currentTag, replacement);
                        replaced = true;
                        x.skipPast(currentTag + ">");
                        return false;
                    }

                    replaced = true;
                    x.skipPast(currentTag + ">");
                    return false;
                }

                if (isReplace && hasIndex && !indexMatches) {
                    arrayKey.put(currentTag, remainingIndex - 1);
                }

                if (!isReplace) {
                    if (hasIndex) {
                        skipCurrentKey = (remainingIndex != 0);
                        arrayKey.put(currentTag, remainingIndex - 1);
                    }

                    if (!targetPath.get(currentNestingDepth).equals(currentTag)) {
                        skipCurrentKey = true;
                    }

                }
            } //--------add replacement logic ends-----------------------------------//
            tagName = (String) token;
            token = null;
            jsonObject = new JSONObject();
            boolean nilAttributeFound = false;
            xmlXsiTypeConverter = null;
            for (;;) {
                if (token == null) {
                    token = x.nextToken();
                }
                // attribute = value
                if (token instanceof String) {
                    string = (String) token;
                    token = x.nextToken();
                    if (token == EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw x.syntaxError("Missing value");
                        }

                        if (config.isConvertNilAttributeToNull()
                                && NULL_ATTR.equals(string)
                                && Boolean.parseBoolean((String) token)) {
                            nilAttributeFound = true;
                        } else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
                                && TYPE_ATTR.equals(string)) {
                            xmlXsiTypeConverter = config.getXsiTypeMap().get(token);
                        } else if (!nilAttributeFound) {
                            Object obj = stringToValue((String) token);
                            if (obj instanceof Boolean) {
                                jsonObject.accumulate(string,
                                        config.isKeepBooleanAsString()
                                                ? ((String) token)
                                                : obj);
                            } else if (obj instanceof Number) {
                                jsonObject.accumulate(string,
                                        config.isKeepNumberAsString()
                                                ? ((String) token)
                                                : obj);
                            } else {
                                jsonObject.accumulate(string, stringToValue((String) token));
                            }
                        }
                        token = null;
                    } else {
                        jsonObject.accumulate(string, "");
                    }


                } else if (token == SLASH) {
                    // Empty tag <.../>
                    if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (config.getForceList().contains(tagName)) {
                        // Force the value to be an array
                        if (nilAttributeFound) {
                            context.append(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.append(tagName, jsonObject);
                        } else {
                            context.put(tagName, new JSONArray());
                        }
                    } else {
                        if (nilAttributeFound) {
                            context.accumulate(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.accumulate(tagName, jsonObject);
                        } else {
                            context.accumulate(tagName, "");
                        }
                    }
                    return false;

                } else if (token == GT) {
                    // Content, between <...> and </...>
                    for (;;) {
                        token = x.nextContent();
                        if (token == null) {
                            if (tagName != null) {
                                throw x.syntaxError("Unclosed tag " + tagName);
                            }
                            return false;
                        } else if (token instanceof String) {
                            string = (String) token;
                            if (string.length() > 0) {
                                if(xmlXsiTypeConverter != null) {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                            stringToValue(string, xmlXsiTypeConverter));
                                } else {
                                    Object obj = stringToValue((String) token);
                                    if (obj instanceof Boolean) {
                                        jsonObject.accumulate(config.getcDataTagName(),
                                                config.isKeepBooleanAsString()
                                                        ? ((String) token)
                                                        : obj);
                                    } else if (obj instanceof Number) {
                                        jsonObject.accumulate(config.getcDataTagName(),
                                                config.isKeepNumberAsString()
                                                        ? ((String) token)
                                                        : obj);
                                    } else {
                                        jsonObject.accumulate(config.getcDataTagName(), stringToValue((String) token));
                                    }
                                }
                            }

                        } else if (token == LT) {

                            if (parseMilestone2(x, jsonObject, tagName, config, currentNestingDepth + 1,
                                    targetPath, targetPathLength, arrayKey, isReplace, mergeToParent, replacement)) {
                                if (config.getForceList().contains(tagName)) {
                                    if (jsonObject.length() == 0) {
                                        context.put(tagName, new JSONArray());
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(config.getcDataTagName()) != null) {
                                        context.append(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        context.append(tagName, jsonObject);
                                    }
                                } else {
                                    if (jsonObject.length() == 0) {
                                        context.accumulate(tagName, "");
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(config.getcDataTagName()) != null) {
                                        context.accumulate(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        if (!config.shouldTrimWhiteSpace()) {
                                            removeEmpty(jsonObject, config);
                                        }
                                        context.accumulate(tagName, jsonObject);
                                    }
                                }
                                return false;
                            }
                        }
                    }
                } else {
                    throw x.syntaxError("Misshaped tag");
                }
            }
        }
    }
    /**
     * Converts an XML input stream into a JSONObject, replacing a sub-object at a specified JSONPointer path.
     *
     * <p>This method is added as part of SWE262P Milestone2 Task2. It performs in-place replacement
     * during parsing, avoiding the need to first build the entire JSON tree before modifying it.
     * This offers performance benefits by allowing early exit from the parser once the target node is handled.</p>
     *
     * @param reader The XML input
     * @param path The JSONPointer path where replacement should occur
     * @param replacement The JSONObject to insert at the given path
     * @return A JSONObject with the sub-object at the given path replaced
     * @throws JSONException if parsing or path manipulation fails
     */
    public static JSONObject toJSONObject(Reader reader, JSONPointer path, JSONObject replacement) throws JSONException {
        JSONObject jo = new JSONObject();
        XMLTokener x = new XMLTokener(reader);

        // Reset shared state
        replaced = false;
        skipCurrentKey = false;

        String[] segments = path.toString().split("/");
        List<String> targetPath = Arrays.stream(segments)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        int targetPathLength = targetPath.size();

        Map<String, Integer> arrayKey = new HashMap<>();
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].matches("\\d+") && i > 0) {
                arrayKey.put(segments[i - 1], Integer.parseInt(segments[i]));
            }
        }

        boolean mergeToParent = !path.toString().endsWith("/");

        while (x.more()) {
            x.skipPast("<");
            if (x.more()) {
                parseMilestone2(x, jo, null, XMLParserConfiguration.ORIGINAL, 0,
                        targetPath, targetPathLength, arrayKey, true, mergeToParent, replacement);
            }
        }

        if (replaced) {
            return jo;
        } else {
            throw new JSONException("Replacement failed or path not found: " + path);
        }
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param string
     *            The source string.
     * @param keepStrings If true, then values will not be coerced into boolean
     *  or numeric values and will instead be left as strings
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string, boolean keepStrings) throws JSONException {
        return toJSONObject(new StringReader(string), keepStrings);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All numbers are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document depending
     * on how flag is set.
     * All booleans are converted as strings, for true, false will not be coerced to
     * booleans but will instead be the exact value as seen in the XML document depending
     * on how flag is set.
     *
     * @param string
     *            The source string.
     * @param keepNumberAsString If true, then numeric values will not be coerced into
     *  numeric values and will instead be left as strings
     * @param keepBooleanAsString If true, then boolean values will not be coerced into
     *  numeric values and will instead be left as strings
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string, boolean keepNumberAsString, boolean keepBooleanAsString) throws JSONException {
        return toJSONObject(new StringReader(string), keepNumberAsString, keepBooleanAsString);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param string
     *            The source string.
     * @param config Configuration options for the parser.
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string, XMLParserConfiguration config) throws JSONException {
        return toJSONObject(new StringReader(string), config);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(Object object) throws JSONException {
        return toString(object, null, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName) {
        return toString(object, tagName, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @param config
     *            Configuration that can control output to XML.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName, final XMLParserConfiguration config)
            throws JSONException {
        return toString(object, tagName, config, 0, 0);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string,
     * either pretty print or single-lined depending on indent factor.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @param config
     *            Configuration that can control output to XML.
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @param indent
     *            The current ident level in spaces.
     * @return
     * @throws JSONException
     */
    private static String toString(final Object object, final String tagName, final XMLParserConfiguration config, int indentFactor, int indent)
            throws JSONException {
        StringBuilder sb = new StringBuilder();
        JSONArray ja;
        JSONObject jo;
        String string;

        if (object instanceof JSONObject) {

            // Emit <tagName>
            if (tagName != null) {
                sb.append(indent(indent));
                sb.append('<');
                sb.append(tagName);
                sb.append('>');
                if(indentFactor > 0){
                    sb.append("\n");
                    indent += indentFactor;
                }
            }

            // Loop thru the keys.
            // don't use the new entrySet accessor to maintain Android Support
            jo = (JSONObject) object;
            for (final String key : jo.keySet()) {
                Object value = jo.opt(key);
                if (value == null) {
                    value = "";
                } else if (value.getClass().isArray()) {
                    value = new JSONArray(value);
                }

                // Emit content in body
                if (key.equals(config.getcDataTagName())) {
                    if (value instanceof JSONArray) {
                        ja = (JSONArray) value;
                        int jaLength = ja.length();
                        // don't use the new iterator API to maintain support for Android
						for (int i = 0; i < jaLength; i++) {
                            if (i > 0) {
                                sb.append('\n');
                            }
                            Object val = ja.opt(i);
                            sb.append(escape(val.toString()));
                        }
                    } else {
                        sb.append(escape(value.toString()));
                    }

                    // Emit an array of similar keys

                } else if (value instanceof JSONArray) {
                    ja = (JSONArray) value;
                    int jaLength = ja.length();
                    // don't use the new iterator API to maintain support for Android
					for (int i = 0; i < jaLength; i++) {
                        Object val = ja.opt(i);
                        if (val instanceof JSONArray) {
                            sb.append('<');
                            sb.append(key);
                            sb.append('>');
                            sb.append(toString(val, null, config, indentFactor, indent));
                            sb.append("</");
                            sb.append(key);
                            sb.append('>');
                        } else {
                            sb.append(toString(val, key, config, indentFactor, indent));
                        }
                    }
                } else if ("".equals(value)) {
                    if (config.isCloseEmptyTag()){
                        sb.append(indent(indent));
                        sb.append('<');
                        sb.append(key);
                        sb.append(">");
                        sb.append("</");
                        sb.append(key);
                        sb.append(">");
                        if (indentFactor > 0) {
                            sb.append("\n");
                        }
                    }else {
                        sb.append(indent(indent));
                        sb.append('<');
                        sb.append(key);
                        sb.append("/>");
                        if (indentFactor > 0) {
                            sb.append("\n");
                        }
                    }

                    // Emit a new tag <k>

                } else {
                    sb.append(toString(value, key, config, indentFactor, indent));
                }
            }
            if (tagName != null) {

                // Emit the </tagName> close tag
                sb.append(indent(indent - indentFactor));
                sb.append("</");
                sb.append(tagName);
                sb.append('>');
                if(indentFactor > 0){
                    sb.append("\n");
                }
            }
            return sb.toString();

        }

        if (object != null && (object instanceof JSONArray ||  object.getClass().isArray())) {
            if(object.getClass().isArray()) {
                ja = new JSONArray(object);
            } else {
                ja = (JSONArray) object;
            }
            int jaLength = ja.length();
            // don't use the new iterator API to maintain support for Android
			for (int i = 0; i < jaLength; i++) {
                Object val = ja.opt(i);
                // XML does not have good support for arrays. If an array
                // appears in a place where XML is lacking, synthesize an
                // <array> element.
                sb.append(toString(val, tagName == null ? "array" : tagName, config, indentFactor, indent));
            }
            return sb.toString();
        }


        string = (object == null) ? "null" : escape(object.toString());
        String indentationSuffix = (indentFactor > 0) ? "\n" : "";
        if(tagName == null){
            return indent(indent) + "\"" + string + "\"" + indentationSuffix;
        } else if(string.length() == 0){
            return indent(indent) + "<" + tagName + "/>" + indentationSuffix;
        } else {
            return indent(indent) + "<" + tagName
                    + ">" + string + "</" + tagName + ">" + indentationSuffix;
        }
    }

    /**
     * Convert a JSONObject into a well-formed, pretty printed element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(Object object, int indentFactor){
        return toString(object, null, XMLParserConfiguration.ORIGINAL, indentFactor);
    }

    /**
     * Convert a JSONObject into a well-formed, pretty printed element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName, int indentFactor) {
        return toString(object, tagName, XMLParserConfiguration.ORIGINAL, indentFactor);
    }

    /**
     * Convert a JSONObject into a well-formed, pretty printed element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @param config
     *            Configuration that can control output to XML.
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName, final XMLParserConfiguration config, int indentFactor)
            throws JSONException {
        return toString(object, tagName, config, indentFactor, 0);
    }

    /**
     * Return a String consisting of a number of space characters specified by indent
     *
     * @param indent
     *          The number of spaces to be appended to the String.
     * @return
     */
    private static final String indent(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    // milestone 5
    private static final ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
    );

    public static Future<JSONObject> toJSONObject(
            Reader reader,
            Consumer<JSONObject> after,
            Consumer<Exception> error
    ) {
        FutureTask<JSONObject> task = new FutureTask<>(new FutureTaskCallable(reader, after, error));
        executor.execute(task);
        return task;
    }

    private static class FutureTaskCallable implements Callable<JSONObject> {
        private final Reader reader;
        private final Consumer<JSONObject> after;
        private final Consumer<Exception> error;

        public FutureTaskCallable(
                Reader reader,
                Consumer<JSONObject> after,
                Consumer<Exception> error
        ) {
            this.reader = reader;
            this.after = after;
            this.error = error;
        }

        @Override
        public JSONObject call() throws Exception {
            JSONObject jo = new JSONObject();
            try {
                XMLTokener x = new XMLTokener(reader);
                while (x.more()) {
                    x.skipPast("<");
                    if (x.more()) {
                        parse(x, jo, null, XMLParserConfiguration.ORIGINAL, 0);
                    }
                }
                after.accept(jo);
                return jo;
            } catch (Exception e) {
                error.accept(e);
                throw e;
            }
        }
    }

    public static class AsyncRunner {
        private List<Future<JSONObject>> tasks = new ArrayList<>();

        public AsyncRunner() {
        }

        public void add(Future<JSONObject> task) {
            this.tasks.add(task);
        }

    }
}
