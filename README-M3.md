# Milestone 3

For the Milestone 3 of SWE262P, the following new functions was added:

```java
static JSONObject toJSONObject(Reader reader, Function func) 
```

This function takes in 3 parameters, a `Reader` object which contains some XML input and a `Function` object that includes a function for converting a `String` (expected type of input: a `String`, and is expected to return another `String`), and a `JSONObject` for replacement. And returns a new `JSONObject` object with tag names replaced, or throw an error if the `Reader` object gives an invalid XML.

The new function is placed in the `XML.java` file.

The test cases of the functions are placed under the `org.json.junit.milestone3.tests` package, and to run the test case, run the following command:

`mvn -Dtest=XMLKeyTransformerTest test`

By implementing the code in the original library code, the function is able to complete the task in one-pass, as the function is writing to a new `JSONObject` while parsing the input XML String. However, in milestone 1, the client code is only able to convert the whole XML String to a `JSONObject`, then convert the keys of this `JSONObject` object.

Thus, implementing this function inside the library code is able to reduce the execution time in half (one-pass vs two-pass), and also resulting in optimization of memory usage. 