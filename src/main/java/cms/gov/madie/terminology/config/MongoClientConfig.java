package cms.gov.madie.terminology.config;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
// import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mongodb.AutoEncryptionSettings;
import com.mongodb.ClientEncryptionSettings;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.vault.DataKeyOptions;
import com.mongodb.client.vault.ClientEncryption;
import com.mongodb.client.vault.ClientEncryptions;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static java.util.Collections.singletonList;

@Configuration
@Slf4j
public class MongoClientConfig {

  @Value("${spring.data.mongodb.uri}")
  private String mongoConnectionUri;

  private final Map<String, Object> keyMap = new HashMap<>();
  private final Map<String, Map<String, Object>> kmsProviders = new HashMap<>();
  private final String keyVaultNamespace = "admin.keyVault";

  // @Bean
  public MongoClientSettingsBuilderCustomizer customizer() {
    return (builder) -> {
      keyMap.put("key", "<local>");
      kmsProviders.put("local", keyMap);

      // AWS
      //      keyMap.put("accessKeyId", "11222333");
      //      keyMap.put("secretAccessKey", "112222333"); // probs can be whatever for localstack
      //      kmsProviders.put("aws", keyMap);

      Map<String, BsonDocument> schemaMap = new HashMap<>();
      schemaMap.put(
          "madie.umlsApiKey",
          // Need a schema that references the new data key
          BsonDocument.parse(
              "{"
                  + "  properties: {"
                  + "    apiKey: {"
                  + "      encrypt: {"
                  + "        keyId: [{"
                  + "          \"$binary\": {"
                  + "            \"base64\": \""
                  + generateLocalKeyId().getBase64KeyId()
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

      AutoEncryptionSettings autoEncryptionSettings =
          AutoEncryptionSettings.builder()
              .keyVaultNamespace(keyVaultNamespace)
              .kmsProviders(kmsProviders)
              .schemaMap(schemaMap)
              .build();

      builder.autoEncryptionSettings(autoEncryptionSettings);

      // AWS
      //      Block<SslSettings.Builder> sslSettings =
      //          sslBuilder -> sslBuilder.enabled(true).invalidHostNameAllowed(true);
      //
      //      builder.applyToSslSettings(sslSettings);
    };
  }

  private LocalKey generateLocalKeyId() {
    ClientEncryptionSettings clientEncryptionSettings =
        ClientEncryptionSettings.builder()
            .keyVaultMongoClientSettings(
                MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(mongoConnectionUri))
                    .build())
            .keyVaultNamespace(keyVaultNamespace)
            .kmsProviders(kmsProviders)
            .build();

    // AWS
    //    BsonString masterKeyArn = new
    // BsonString("arn:aws:kms:us-east-1:000000000000:key/6b92c992-474c-4d16-91f5-5e01f073bb43");
    //    BsonString masterKeyRegion = new BsonString("us-east-1");
    //    BsonString awsEndpoint = new BsonString("https://0.0.0.0:4566");
    //    DataKeyOptions dataKeyOptions = new DataKeyOptions().masterKey(
    //        new BsonDocument()
    //            .append("key", masterKeyArn)
    //            .append("region", masterKeyRegion)
    //            .append("endpoint", awsEndpoint))
    //        .keyAltNames(singletonList("me"));
    //    BsonBinary dataKeyId = clientEncryption.createDataKey("aws", dataKeyOptions);

    BsonBinary dataKeyId;
    try (ClientEncryption clientEncryption = ClientEncryptions.create(clientEncryptionSettings)) {
      dataKeyId =
          clientEncryption.createDataKey(
              "local", new DataKeyOptions().keyAltNames(singletonList("me")));
    }
    String base64DataKeyId = Base64.getEncoder().encodeToString(dataKeyId.getData());

    System.out.println("\n\nbase64DataKeyId = " + base64DataKeyId + "\n\n");
    log.info("\n\nbase64DataKeyId = " + base64DataKeyId + "\n\n");

    return new LocalKey(dataKeyId.asUuid(), base64DataKeyId);
  }

  @Getter
  private static class LocalKey {
    private final UUID uuidKeyId;
    private final String base64KeyId;

    public LocalKey(UUID uuidKeyId, String base64KeyId) {
      this.uuidKeyId = uuidKeyId;
      this.base64KeyId = base64KeyId;
    }
  }
}
