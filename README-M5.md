# Milestone 5 – Async support for **JSONObject**

## What is Added

| Item                                                                                                             | Description                                                                                                                                                                                                                                                       |
|------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Future<JSONObject> XMLUtils.toJSONObject(Reader reader, Consumer<JSONObject> after, Consumer<Exception> error)` | **Non-blocking** conversion. Parses the XML read from `reader` into a `JSONObject` on a background thread. When parsing finishes it invokes `after.accept(result)`; when failure it calls `error.accept(ex)` and throws the exception into the returned `Future`. |
| `AsyncRunner`                                                                                                    | Tiny task aggregator. Call `add(Future<JSONObject> task)` to collect jobs, then wait for them all (e.g. `forEach(Future::get)`).                                                                                                                                  |
| `ExecutorService`                                                                              | The default thread pool (size = available CPU cores). If you prefer a custom pool you can swap it out before calling the API (e.g. add `XMLUtils.setExecutor(...)`).                                                                                              |

Input: XML of different sizes (where they will be parsed concurrently)

---

## Run the test class
```bash
mvn -Dtest=org.json.junit.milestone5.tests.JSONObjectAsyncTest test