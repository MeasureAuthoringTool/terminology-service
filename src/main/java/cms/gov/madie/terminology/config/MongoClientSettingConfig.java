package cms.gov.madie.terminology.config;

import java.io.File;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Binary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mongodb.AutoEncryptionSettings;
import com.mongodb.ClientEncryptionSettings;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.vault.DataKeyOptions;
import com.mongodb.client.vault.ClientEncryption;
import com.mongodb.client.vault.ClientEncryptions;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class MongoClientSettingConfig {

  @Value("${spring.data.mongodb.uri}")
  private String mongoConnectionUri;

  private static final String keyVaultNamespace = "encryption.__keyVault";

  @Bean
  public MongoClientSettingsBuilderCustomizer customizer() {

    // temp: get the Locally-Managed Master Key
    byte[] localMasterKey = getMasterKey();
    log.info("\n\nstep 2: localMasterKey = " + localMasterKey.toString() + "\n\n");

    // Specify KMS Provider Settings
    Map<String, Map<String, Object>> kmsProviders = setKMSProviders(localMasterKey);

    // Create a Data Encryption Key
    String base64KeyId = createDataEncryptionKey(kmsProviders);

    // Verify that the Data Encryption Key was Created
    verifyDataEncryptionKey(base64KeyId);

    return getMongoClientSettingsBuilderCustomizer(kmsProviders, base64KeyId);
  }

  protected byte[] getMasterKey() {
    byte[] localMasterKey = new byte[96];
    new SecureRandom().nextBytes(localMasterKey);
    return localMasterKey;
  }

  protected File getFileFromResource(String filePath) {
    ClassLoader classloader = Thread.currentThread().getContextClassLoader();
    return new File(Objects.requireNonNull(classloader.getResource(filePath)).getFile());
  }

  protected Map<String, Map<String, Object>> setKMSProviders(byte[] localMasterKey) {
    Map<String, Object> keyMap = new HashMap<String, Object>();
    keyMap.put("key", localMasterKey);
    Map<String, Map<String, Object>> kmsProviders = new HashMap<String, Map<String, Object>>();
    kmsProviders.put("local", keyMap);
    // for AWS
    // keyMap.put("accessKeyId", "testAccessKeyId");
    // keyMap.put("secretAccessKey", "testSecretAccessKey");
    // kmsProviders.put("aws", keyMap);
    return kmsProviders;
  }

  protected String createDataEncryptionKey(Map<String, Map<String, Object>> kmsProviders) {

    ClientEncryptionSettings clientEncryptionSettings =
        ClientEncryptionSettings.builder()
            .keyVaultMongoClientSettings(
                MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(mongoConnectionUri))
                    .build())
            .keyVaultNamespace(keyVaultNamespace)
            .kmsProviders(kmsProviders)
            .build();
    log.info("\n\ncreateDataEncryptionKey(): test 1");
    ClientEncryption clientEncryption = ClientEncryptions.create(clientEncryptionSettings);
    log.info("\n\ncreateDataEncryptionKey(): test 2");

    // temp: use local
    // BsonBinary dataKeyId = clientEncryption.createDataKey(kmsProvider, new DataKeyOptions());
    BsonBinary dataKeyId = clientEncryption.createDataKey("local", new DataKeyOptions());
    System.out.println("DataKeyId [UUID]: " + dataKeyId.asUuid());
    String base64DataKeyId = Base64.getEncoder().encodeToString(dataKeyId.getData());
    System.out.println("DataKeyId [base64]: " + base64DataKeyId);

    return base64DataKeyId;
  }

  protected void verifyDataEncryptionKey(String base64KeyId) {
    String keyVaultDb = "encryption";
    String keyVaultCollection = "__keyVault";

    MongoClient mongoClient = MongoClients.create(mongoConnectionUri);
    MongoCollection<Document> collection =
        mongoClient.getDatabase(keyVaultDb).getCollection(keyVaultCollection);
    Bson query = Filters.eq("_id", new Binary((byte) 4, Base64.getDecoder().decode(base64KeyId)));
    Document doc = collection.find(query).first();
    System.out.println(doc);

    BsonDocument bsondoc = collection.withDocumentClass(BsonDocument.class).find(query).first();
    System.out.println("bsondoc = " + bsondoc);
  }

  protected MongoClientSettingsBuilderCustomizer getMongoClientSettingsBuilderCustomizer(
      Map<String, Map<String, Object>> kmsProviders, String base64KeyId) {
    return (builder) -> {
      Map<String, BsonDocument> schemaMap = MongoClientSettingSchemaMap.getSchemaMap(base64KeyId);

      AutoEncryptionSettings autoEncryptionSettings =
          AutoEncryptionSettings.builder()
              .keyVaultNamespace(keyVaultNamespace)
              .kmsProviders(kmsProviders)
              .schemaMap(schemaMap)
              .extraOptions(getExtraOptions())
              .build();

      builder.autoEncryptionSettings(autoEncryptionSettings);
    };
  }

  protected Map<String, Object> getExtraOptions() {
    Map<String, Object> extraOptions = new HashMap<String, Object>();
    extraOptions.put("mongocryptdBypassSpawn", true);

    return extraOptions;
  }
}
