# terminology-service

Project to provide service of VSAC, etc, terminology to MADiE.

To build 
```
mvn clean verify ```

To run, first add 
```
port: 8083 ``` in application.yml file, for server port configuration, and then run
```
mvn install spring-boot:run ```

or
```
docker compose up ```


To test actuator locally
```
http://localhost:8083/api/actuator/ ```

To test app locally
```
http://localhost:8083/api ```
should give response unauthorized (HTTP ERROR 401)
