# Terminology Service

The Terminology Service retrieves terminology through the VSAC SVS APIs and VSAC FHIR Terminology Server, and populates and also maintains terminology data in VSES.

## Build and run

Requirements: Java 17 and Maven. A local MongoDB instance must be available at `localhost:27017` when running the service directly.

Build the service:

```bash
mvn clean install
```

Run it locally:

```bash
mvn spring-boot:run
```

To run the service and MongoDB with Docker Compose, first build the application JAR and then start the containers:

```bash
mvn clean package
docker compose up --build
```

The service listens on port `8081`. The health actuator endpoint is available at [http://localhost:8081/api/actuator/health](http://localhost:8081/api/actuator/health).

## VSES terminology ingestion

**VSAC is the source of truth for VSAC-prefixed value sets.** Automated ingestion stores only TX.FHIR expansions in VSES, using them temporarily when VSAC confirms a value set is unavailable. Once VSAC supports it, the matching VSES record is removed.

### Refresh triggers

The following APIs require the `MADIE-ADMIN` role. Paths include the `/api` context path.

| API | Scope |
| --- | --- |
| `GET /api/terminology/admin/implementation-guides/update-value-sets?ig={ig}&version={version}` | Refresh value set dependencies for a specific implementation guide (IG) and version. Both parameters must be nonblank. |
| `GET /api/terminology/admin/implementation-guides/update-value-sets` | Refresh value set dependencies for all loaded IGs. This also applies if either parameter is missing or blank. |

These requests start an asynchronous refresh and return `202 Accepted`; the response does not indicate that ingestion has completed.

The scheduled refresh processes all loaded IGs using the same flow. By default, it runs every Monday at 1:00 AM in `America/New_York`. Configure it with `VSES_VALUESET_UPDATE_CRON` and `VSES_VALUESET_UPDATE_ZONE`, or set `VSES_VALUESET_UPDATE_CRON` to `-` to disable it.

### Ingestion flow

Collect the value set dependencies for the selected IG/version or all loaded IGs, then process each value set:

| Condition | Action |
| --- | --- |
| URL starts with `http://cts.nlm.nih.gov/fhir/` | Check VSAC availability by URL and, when nonblank, version. This is a count-only search, not an expansion request. |
| Available in VSAC | Delete the VSES record with the dependency's exact URL/version, if present, including manually modified records. Otherwise, no cleanup is needed. Skip TX.FHIR; other versions remain untouched. |
| Confirmed absent from VSAC | Request its expansion from TX.FHIR. |
| VSAC lookup fails or returns an invalid response | Log the issue and skip the value set, preserving existing VSES data. |
| URL does not have the VSAC prefix | Request its expansion directly from TX.FHIR. |

Save successful TX.FHIR expansions to VSES by URL/version: insert new records or update existing ones unless marked `manuallyModified`. Missing or failed expansions are logged and not saved; existing data is retained. There is no VSAC expansion fallback. An admin may need to add the value set manually through the UI if an expansion cannot be retrieved.

### Manual data maintenance

Admins can create records with `POST /api/terminology/admin/value-set` or update them with `PUT /api/terminology/admin/value-set`. These APIs validate and save the supplied payload without fetching expansions. Records are marked `manuallyModified`: automated refreshes cannot overwrite them, but still delete them when the matching VSAC-prefixed value set becomes available in VSAC.
