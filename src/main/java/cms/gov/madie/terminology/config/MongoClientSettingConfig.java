package cms.gov.madie.terminology.config;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import static java.util.Collections.singletonList;

import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mongodb.AutoEncryptionSettings;
import com.mongodb.ClientEncryptionSettings;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.vault.DataKeyOptions;
import com.mongodb.client.vault.ClientEncryption;
import com.mongodb.client.vault.ClientEncryptions;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class MongoClientSettingConfig {

  @Value("${spring.data.mongodb.uri}")
  private String mongoConnectionUri;

  @Value("${environment}")
  private String environment;

  String rootPath = System.getProperty("user.dir");
  String keyPath = rootPath + "/" + "master-key.txt";

  private static final String keyVaultNamespace = "encryption.__keyVault";

  @Bean
  public MongoClientSettingsBuilderCustomizer customizer() {

    byte[] masterKey = null;

    if ("local".equalsIgnoreCase(environment)) {
      // temp: get the Locally-Managed Master Key
      masterKey = readKeyLocally();
      if (masterKey == null) {
        masterKey = getMasterKey();
        writeKeyLocally(masterKey);
      }
    }

    // Specify KMS Provider Settings
    Map<String, Map<String, Object>> kmsProviders = setKMSProviders(masterKey);

    // Create a Data Encryption Key
    String base64KeyId = createDataEncryptionKey(kmsProviders);

    return getMongoClientSettingsBuilderCustomizer(kmsProviders, base64KeyId);
  }

  protected byte[] readKeyLocally() {
    byte[] content = null;
    try {
      content = Files.readAllBytes(Paths.get(keyPath));
    } catch (IOException e) {
      // e.printStackTrace();
      log.error("readKeyLocally(): IOException -> " + e.getMessage());
    }
    return content;
  }

  protected byte[] getMasterKey() {
    byte[] localMasterKey = new byte[96];
    new SecureRandom().nextBytes(localMasterKey);
    return localMasterKey;
  }

  protected void writeKeyLocally(byte[] localMasterKey) {
    try (FileOutputStream stream = new FileOutputStream(keyPath)) {
      stream.write(localMasterKey);
      log.debug("master key created and writen to " + keyPath);
    } catch (FileNotFoundException e) {
      log.error("CreateMasterKeyFile(): FileNotFoundException -> " + e.getMessage());
    } catch (IOException e) {
      log.error("CreateMasterKeyFile(): IOException -> " + e.getMessage());
    }
  }

  protected Map<String, Map<String, Object>> setKMSProviders(byte[] localMasterKey) {
    Map<String, Object> keyMap = new HashMap<String, Object>();
    Map<String, Map<String, Object>> kmsProviders = new HashMap<String, Map<String, Object>>();
    if ("local".equalsIgnoreCase(environment)) {
      keyMap.put("key", localMasterKey);
      kmsProviders.put("local", keyMap);
    } else {
      // temp: for AWS, Authenticate with IAM Roles in Production
      keyMap.put("accessKeyId", "testAccessKeyId");
      keyMap.put("secretAccessKey", "testSecretAccessKey");
      kmsProviders.put("aws", keyMap);
    }
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
    ClientEncryption clientEncryption = ClientEncryptions.create(clientEncryptionSettings);

    BsonBinary dataKeyId = null;
    if ("local".equalsIgnoreCase(environment)) {
      dataKeyId = clientEncryption.createDataKey("local", new DataKeyOptions());
    } else {
      // temp: AWS
      BsonString masterKeyArn =
          new BsonString(
              "arn:aws:kms:us-east-1:000000000000:key/6b92c992-474c-4d16-91f5-5e01f073bb43");
      BsonString masterKeyRegion = new BsonString("us-east-1");
      BsonString awsEndpoint = new BsonString("https://0.0.0.0:4566");
      DataKeyOptions dataKeyOptions =
          new DataKeyOptions()
              .masterKey(
                  new BsonDocument()
                      .append("key", masterKeyArn)
                      .append("region", masterKeyRegion)
                      .append("endpoint", awsEndpoint))
              .keyAltNames(singletonList("umls"));
      dataKeyId = clientEncryption.createDataKey("aws", dataKeyOptions);
    }
    String base64DataKeyId = Base64.getEncoder().encodeToString(dataKeyId.getData());
    return base64DataKeyId;
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
      //      if(!"local".equalsIgnoreCase(environment)) {
      //      	// AWS
      //        Block<SslSettings.Builder> sslSettings =
      //                  sslBuilder -> sslBuilder.enabled(true).invalidHostNameAllowed(true);
      //
      //        builder.applyToSslSettings(sslSettings);
      //      }
    };
  }

  protected Map<String, Object> getExtraOptions() {
    Map<String, Object> extraOptions = new HashMap<String, Object>();
    extraOptions.put("mongocryptdBypassSpawn", true);

    return extraOptions;
  }
}
