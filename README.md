# CMS-0057 Prior Authorization Service

A Spring Boot microservice implementing **CMS-0057-F** (Advancing Interoperability and Improving Prior Authorization Processes) using the Da Vinci FHIR implementation guides for **CRD**, **DTR**, and **PAS**.

| Spec | Version |
|---|---|
| Spring Boot | 3.4.1 |
| HAPI FHIR | 7.0.3 (R4) |
| Java | 21 |
| Da Vinci IGs | CRD STU 2.1 · DTR STU 3 · PAS STU 2 |

---

## Architecture

```
EHR / Provider System
       │
       ├── CDS Hooks ──────▶ CRD  /cds-services/**
       │                          (coverage requirements discovery)
       │
       ├── SMART Launch ───▶ DTR  /fhir/Questionnaire/**
       │                          (documentation templates & rules)
       │
       └── FHIR Operations ▶ PAS  /fhir/Claim/$submit
                                   /fhir/Claim/$inquire
                                   (prior authorization support)
```

CMS-0057-F response time mandates enforced automatically:

- **Urgent (expedited):** ≤ 72 hours
- **Standard:** ≤ 7 calendar days

---

## Prerequisites

| Tool | Min version | Install |
|---|---|---|
| Java JDK | 21 | [Eclipse Temurin](https://adoptium.net) |
| Docker + Compose | 24 / 2.x | [Docker Desktop](https://www.docker.com/products/docker-desktop) |
| Git | any | system |

No Maven installation required — the Maven wrapper (`./mvnw`) is included.

---

## Installation

```bash
git clone https://github.com/bhaskarkollu/cms0057.git
cd cms0057
```

---

## Running the Service

### Option A — Docker Compose (recommended)

Starts the service **and** a persistent H2 database server in one command.

```bash
docker compose up --build
```

| Container | URL |
|---|---|
| Prior Auth API | http://localhost:8080 |
| H2 Web Console | http://localhost:81 |

**H2 Console connection settings:**
```
JDBC URL:  jdbc:h2:tcp://h2:1521/cms0057db
Username:  sa
Password:  (leave blank)
```

Stop and keep data:
```bash
docker compose down
```

Stop and delete data volume:
```bash
docker compose down -v
```

---

### Option B — Local (Maven wrapper)

Requires Java 21+.

```bash
./mvnw spring-boot:run
```

The service starts on **http://localhost:8080** using an in-memory H2 database.  
H2 console available at **http://localhost:8080/h2-console**  
(JDBC URL: `jdbc:h2:mem:cms0057db`, Username: `sa`, Password: blank)

---

## API Reference

### FHIR Capability Statement

```
GET http://localhost:8080/fhir/metadata
```

Lists every supported resource type, operation, and implemented Da Vinci IG.

---

### CRD — Coverage Requirements Discovery

CDS Hooks endpoints. The EHR calls these when a clinician is placing orders.

#### Discover available hooks (public — no auth)

```
GET http://localhost:8080/cds-services
```

#### Order-sign hook

```
POST http://localhost:8080/cds-services/cms0057-crd-order-sign
Content-Type: application/json

{
  "hookInstance": "d1577c69-dfbe-44ad-ba6d-3e05e953b2ea",
  "hook": "order-sign",
  "context": {
    "patientId": "patient-001",
    "userId": "Practitioner/prac-001",
    "draftOrders": {
      "resourceType": "Bundle",
      "type": "collection",
      "entry": [{
        "resource": {
          "resourceType": "ServiceRequest",
          "id": "sr-001",
          "status": "draft",
          "intent": "order",
          "code": {
            "coding": [{
              "system": "http://www.ama-assn.org/go/cpt",
              "code": "77065",
              "display": "Diagnostic mammography"
            }]
          },
          "subject": { "reference": "Patient/patient-001" }
        }
      }]
    }
  }
}
```

Returns CDS Hook **cards** indicating:
- `"Prior Authorization Required"` (indicator: `warning`) — with a SMART link to launch DTR
- `"No Prior Authorization Required"` (indicator: `info`)

Other supported hooks:

| Hook | Endpoint |
|---|---|
| order-select | `POST /cds-services/cms0057-crd-order-select` |
| appointment-book | `POST /cds-services/cms0057-crd-appointment-book` |

CPT codes that trigger PA-required cards: `77065–77067` (mammography), `70551/70553` (MRI brain), `93306–93308` (echocardiography), `27447` (total knee replacement), `43239` (upper GI endoscopy).

---

### DTR — Documentation Templates and Rules

FHIR endpoints used by the SMART on FHIR DTR app to collect clinical documentation.

#### Get questionnaire by service code

```
GET http://localhost:8080/fhir/Questionnaire?serviceCode=77065
Accept: application/fhir+json
```

#### Get questionnaire by ID

```
GET http://localhost:8080/fhir/Questionnaire/mammography-pa
Accept: application/fhir+json
```

Available questionnaire IDs:

| ID | Use case |
|---|---|
| `mammography-pa` | Mammography (CPT 77065–77067) |
| `mri-brain-pa` | MRI Brain (CPT 70551, 70553) |
| `echo-pa` | Echocardiography (CPT 93306–93308) |
| `total-knee-pa` | Total knee replacement (CPT 27447) |
| `generic-pa-{code}` | Any other CPT code |

#### Adaptive form — next question with pre-populated answers

```
GET http://localhost:8080/fhir/Questionnaire/mammography-pa/$next-question?patient=patient-001
Accept: application/fhir+json
```

Returns a Bundle containing the `Questionnaire` and a pre-populated `QuestionnaireResponse`.

#### Submit completed documentation

```
POST http://localhost:8080/fhir/QuestionnaireResponse
Content-Type: application/fhir+json

{
  "resourceType": "QuestionnaireResponse",
  "questionnaire": "http://hl7.org/fhir/us/davinci-dtr/Questionnaire/mammography-pa",
  "status": "completed",
  "subject": { "reference": "Patient/patient-001" },
  "authored": "2026-07-02T10:00:00Z",
  "item": [{
    "linkId": "clinical-indication",
    "answer": [{ "valueString": "Screening per USPSTF guidelines, age 45" }]
  }]
}
```

---

### PAS — Prior Authorization Support

#### Submit a prior authorization request

```
POST http://localhost:8080/fhir/Claim/$submit
Content-Type: application/fhir+json
```

Request body is a Da Vinci PAS Bundle containing a `Claim` (profile: `profile-claim`) plus supporting resources.

**Minimal example — standard PA request:**

```json
{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": [{
    "resource": {
      "resourceType": "Claim",
      "id": "claim-001",
      "meta": {
        "profile": ["http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claim"]
      },
      "status": "active",
      "use": "preauthorization",
      "type": {
        "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/claim-type", "code": "professional" }]
      },
      "priority": {
        "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/processpriority", "code": "normal" }]
      },
      "patient": { "reference": "Patient/patient-001" },
      "provider": { "reference": "Practitioner/prac-001" },
      "created": "2026-07-02T10:00:00Z",
      "insurance": [{
        "sequence": 1,
        "focal": true,
        "coverage": { "reference": "Coverage/coverage-001" }
      }],
      "item": [{
        "sequence": 1,
        "productOrService": {
          "coding": [{ "system": "http://www.ama-assn.org/go/cpt", "code": "77065" }]
        }
      }]
    }
  }]
}
```

For an **urgent (expedited)** request, set priority code to `"stat"` — the SLA deadline becomes 72 hours instead of 7 days.

**Response — pended (manual review required):**
```json
{
  "resourceType": "Bundle",
  "entry": [{
    "resource": {
      "resourceType": "ClaimResponse",
      "use": "preauthorization",
      "outcome": "queued",
      "disposition": "PA request received and pending review. Decision required by ..."
    }
  }]
}
```

**Response — auto-approved** (for CPT codes `99213–99215`, `G0101`, `G0102`):
```json
{
  "resourceType": "ClaimResponse",
  "outcome": "complete",
  "disposition": "Prior Authorization APPROVED. Auth#: PA-1234567890",
  "preAuthRef": "PA-1234567890"
}
```

#### Query PA status

```
POST http://localhost:8080/fhir/Claim/$inquire
Content-Type: application/fhir+json

{
  "resourceType": "Parameters",
  "parameter": [{ "name": "claimId", "valueString": "claim-001" }]
}
```

Returns current `ClaimResponse` with status: `queued` · `pended` · `approved` · `denied` · `partial`.

#### List PA requests by patient

```
GET http://localhost:8080/fhir/Claim?patient=patient-001
Accept: application/fhir+json
```

---

## Running Tests

```bash
./mvnw test
```

Expected output:
```
Tests run: 5  ...  CrdControllerTest
Tests run: 5  ...  DtrControllerTest
Tests run: 8  ...  PasControllerTest
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Run a single test class:
```bash
./mvnw test -Dtest=PasControllerTest
```

Run a single test method:
```bash
./mvnw test -Dtest="PasControllerTest#submitPriorAuth_autoApprovableService_returnsApproved"
```

---

## Configuration

All settings can be overridden via environment variables.

| Variable | Default | Description |
|---|---|---|
| `PAYER_NAME` | `CMS0057 Demo Payer` | Payer display name in CDS cards and ClaimResponse |
| `PAYER_NPI` | `1234567890` | Payer NPI (National Provider Identifier) |
| `PAYER_ID` | `DEMO001` | Internal payer identifier |
| `OAUTH2_ISSUER_URI` | `http://localhost:9000` | SMART on FHIR / OAuth2 token issuer |
| `DTR_SMART_LAUNCH_URL` | `http://localhost:3000/dtr` | DTR SMART app base URL |
| `SPRING_DATASOURCE_URL` | H2 in-memory | JDBC datasource URL |
| `SPRING_PROFILES_ACTIVE` | `test` | Set to `default` to enable OAuth2 enforcement |

**Example — production-like Docker Compose override:**
```bash
PAYER_NAME="Acme Health Plan" \
PAYER_NPI=9876543210 \
OAUTH2_ISSUER_URI=https://auth.acme.com \
SPRING_PROFILES_ACTIVE=default \
docker compose up
```

---

## Security

By default (`SPRING_PROFILES_ACTIVE=test`) all endpoints are open — suitable for local development and testing.

Set `SPRING_PROFILES_ACTIVE=default` to enforce OAuth2 JWT (SMART on FHIR) on all endpoints except:
- `GET /cds-services` — public per CDS Hooks spec
- `GET /fhir/metadata` — public FHIR capability statement
- `GET /actuator/health` — health probe

---

## Da Vinci Implementation Guides

| IG | Version | URL |
|---|---|---|
| Prior Authorization Support (PAS) | STU 2 | https://hl7.org/fhir/us/davinci-pas |
| Coverage Requirements Discovery (CRD) | STU 2.1 | https://hl7.org/fhir/us/davinci-crd |
| Documentation Templates and Rules (DTR) | STU 3 | https://hl7.org/fhir/us/davinci-dtr |
