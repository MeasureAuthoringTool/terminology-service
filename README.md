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

**Automated ingestion populates and refreshes VSES data only with expansions retrieved from TX.FHIR. Expansions retrieved from VSAC are not saved to VSES by this flow.** VSAC is used only to check whether a value set is available before deciding whether to request its expansion from TX.FHIR.

### Refresh triggers

The following APIs require the `MADIE-ADMIN` role. Paths include the `/api` context path.

| API | Scope |
| --- | --- |
| `GET /api/terminology/admin/implementation-guides/update-value-sets?ig={ig}&version={version}` | Refresh value set dependencies for a specific implementation guide (IG) and version. Both parameters must be nonblank. |
| `GET /api/terminology/admin/implementation-guides/update-value-sets` | Refresh value set dependencies for all loaded IGs. This also applies if either parameter is missing or blank. |

These requests start an asynchronous refresh and return `202 Accepted`; the response does not indicate that ingestion has completed.

The scheduled refresh processes all loaded IGs using the same flow. By default, it runs every Monday at 1:00 AM in `America/New_York`. Configure it with `VSES_VALUESET_UPDATE_CRON` and `VSES_VALUESET_UPDATE_ZONE`, or set `VSES_VALUESET_UPDATE_CRON` to `-` to disable it.

### Ingestion flow

1. Collect the value set dependencies for the selected IG/version or all loaded IGs.
2. For canonical URLs beginning with `http://cts.nlm.nih.gov/fhir/`, check VSAC for the matching URL and, when provided, version using a `ValueSet` search with `_summary=count`. **This is an availability check, not an expansion request.**
   - If VSAC contains the value set, skip it without requesting an expansion or saving it to VSES.
   - If VSAC confirms no match, request the expansion from TX.FHIR.
   - If VSAC returns an error or invalid response, log the issue and skip the value set.
3. For other canonical URLs, request the expansion directly from TX.FHIR, without checking VSAC.
4. Save only successful TX.FHIR expansions to VSES, inserting new records or updating existing records matched by URL and version. Existing records marked `manuallyModified` are not overwritten. Failed or missing expansions are not saved, and there is no VSAC expansion fallback.

### Manual data maintenance

Separately, admins can create records with `POST /api/terminology/admin/value-set` or update existing records with `PUT /api/terminology/admin/value-set`. These APIs validate and save the supplied value set payload; they do not retrieve expansions from TX.FHIR or VSAC. Records saved through these APIs are marked `manuallyModified` and protected from automated refreshes.
