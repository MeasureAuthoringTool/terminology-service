package cms.gov.madie.terminology.config;

import java.util.HashMap;
import java.util.Map;

import org.bson.BsonDocument;

public class MongoClientSettingSchemaMap {

  public static Map<String, BsonDocument> getSchemaMap(String base64KeyId) {
    Map<String, BsonDocument> schemaMap = new HashMap<>();
    schemaMap.put(
        "terminology.umlsUser",
        // schema that references the data key
        BsonDocument.parse(
            "{"
                + "  properties: {"
                + "    apiKey: {"
                + "      encrypt: {"
                + "        keyId: [{"
                + "          \"$binary\": {"
                + "            \"base64\": \""
                + base64KeyId
                + "\","
                + "            \"subType\": \"04\""
                + "          }"
                + "        }],"
                + "        bsonType: \"string\","
                + "        algorithm: \"AEAD_AES_256_CBC_HMAC_SHA_512-Deterministic\""
                + "      }"
                + "    },"
                + "    harpId: {"
                + "      encrypt: {"
                + "        keyId: [{"
                + "          \"$binary\": {"
                + "            \"base64\": \""
                + base64KeyId
                + "\","
                + "            \"subType\": \"04\""
                + "          }"
                + "        }],"
                + "        bsonType: \"string\","
                + "        algorithm: \"AEAD_AES_256_CBC_HMAC_SHA_512-Deterministic\""
                + "      }"
                + "    }"
                + "  },"
                + "  \"bsonType\": \"object\""
                + "}"));

    return schemaMap;
  }
}
