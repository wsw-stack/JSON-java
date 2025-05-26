# Milestone 4 – Stream support for **JSONObject**

## What is Added

| Item | Description |
|------|-------------|
| `JSONObject.JSONNode` | Immutable leaf wrapper containing an absolute `path` and its `value`. |
| `Stream<JSONNode> JSONObject.toStream()` | Depth-first, **lazy** flattening of any `JSONObject/JSONArray` into a `Stream`. Only leaf nodes are emitted (low memory footprint). |

Path conventions
* Object keys: `/parent/child`
* Array items:  `/array[0]/child`

---

## Run the test class
```bash
mvn -Dtest=org.json.junit.milestone4.tests.JSONObjectStreamTest test