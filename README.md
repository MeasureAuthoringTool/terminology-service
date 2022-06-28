# terminology-service

Project to provide service of VSAC, etc, terminology to MADiE.

To build 
```
mvn clean verify ```


Clone the support-data repository for the latest code system mapping json file:
```
https://github.com/MeasureAuthoringTool/support-data
```
<br />

Update the configuration in application.yml to use the local json file instead of dev instance:
<br />
```
code-system-entry-url: file:/Users/{git repo location}/support-data/dev/madie/code-system-entry.json
```
<br />

To run:
```
mvn install spring-boot:run ```

or
```
docker compose up ```


To test actuator locally
```
http://localhost:8081/api/actuator/ ```

To test app locally
```
http://localhost:8081/api ```
should give response unauthorized (HTTP ERROR 401)
