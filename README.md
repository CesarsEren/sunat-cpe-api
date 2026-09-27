# sunat-cpe-api

API REST pequeña, autocontenida y sin base de datos para enviar **Comprobantes de Pago Electrónicos (CPE)** a SUNAT - Perú.

La API **no firma XML**. Recibe un `.zip` ya firmado por el cliente (UBL 2.1 con firma digital XAdES-BES generada con cualquier herramienta — incluyendo el flujo actual del proyecto `encargaefact`), lo reenvía al `BillService` de SUNAT (Producción o Beta) y devuelve la respuesta parseada (CDR).

## Características

- Spring Boot 1.5.8 + Java 8 + Maven (idéntico stack al proyecto `encargaefact`)
- Sin base de datos, sin Spring Security, sin frontend
- Soporta los 4 tipos de CPE: **Factura (01), Boleta (03), Nota de Crédito (06), Nota de Débito (05)**
- Mismos stubs JAX-WS del proyecto original (envueltos en el paquete `pe.encarga.sunatapi.soap`)
- SOAP header WS-Security UsernameToken (mismo patrón del proyecto actual)
- Logs con Apache Commons Logging + Log4j

## Endpoints

### Envío directo a SUNAT (BillService SOAP)

| Método | Ruta                | Content-Type            | Descripción |
|--------|---------------------|--------------------------|-------------|
| POST   | `/api/v1/cpe/send`     | `multipart/form-data`    | Envía el `.zip` firmado a SUNAT (PRD o BETA) |
| POST   | `/api/v1/cpe/send-base64` | `application/json`   | Igual que `/send` pero el zip viaja en Base64 dentro de un JSON |
| POST   | `/api/v1/cpe/status`   | `application/json`       | Consulta el estado de un ticket (sendSummary/sendPack) |
| POST   | `/api/v1/cpe/health`   | `application/json`       | Health-check |

### Envío de Nota de Crédito vía GRT (REST SEE)

| Método | Ruta                | Content-Type            | Descripción |
|--------|---------------------|--------------------------|-------------|
| POST   | `/api/v1/notacredito/send`       | `multipart/form-data` | Envía el XML UBL firmado a GRT (Fase 2) → SUNAT |
| POST   | `/api/v1/notacredito/send-base64`| `application/json`    | Igual que `/send` pero el XML viaja en Base64 dentro de un JSON |

Esta ruta **solo aplica a Nota de Crédito (tipo 07)**. Internamente la API hace `POST {app.api.grt.path-enviar-betta|prd}?usuario=...&contrasena=...` con el XML como `application/zip` y devuelve la respuesta GRT mapeada al shape `ResponseGeneraXml` (ver sección "Envío de Nota de Crédito vía GRT" más abajo).

El ambiente (PRD/BETA) y las credenciales GRT se configuran en el servidor (`sunat.production` y `app.api.grt.*`); el cliente **no** envía credenciales en el request.

## Formato esperado del archivo `.zip`

```
{RUC}-{TIPO}-{SERIE}-{NUMERO}.zip
```

Donde:

- `RUC`: 11 dígitos (ej. `20600520033`)
- `TIPO`: `01` (Factura), `03` (Boleta), `05` (Nota de Débito), `06` (Nota de Crédito)
- `SERIE`: 4 caracteres alfanuméricos en mayúscula (ej. `F001`, `B001`, `FC01`)
- `NUMERO`: 1 a 8 dígitos (ej. `1`, `00001234`)

Dentro del `.zip` debe ir un único archivo `.xml` con el mismo nombre base, ya firmado.

Ejemplo:

```
20600520033-01-F001-00000001.zip
 └── 20600520033-01-F001-00000001.xml   (UBL 2.1 firmado)
```

## Ejemplos de uso

### 1) Health-check

```bash
curl -X POST http://localhost:8085/api/v1/cpe/health
```

```json
{
  "status": "UP",
  "defaultProduction": false,
  "connectTimeoutMs": 15000,
  "requestTimeoutMs": 60000
}
```

### 2) Envío con multipart

```bash
curl -X POST http://localhost:8085/api/v1/cpe/send \
  -F "file=@/ruta/20600520033-01-F001-00000001.zip" \
  -F "username=20600520033MODDATOS" \
  -F "password=MiClaveSecreta" \
  -F "production=false"
```

### 3) Envío con JSON + Base64

```bash
B64=$(base64 -w0 20600520033-01-F001-00000001.zip)
curl -X POST http://localhost:8085/api/v1/cpe/send-base64 \
  -H "Content-Type: application/json" \
  -d '{
        "filename": "20600520033-01-F001-00000001.zip",
        "fileBase64": "'"$B64"'",
        "username": "20600520033MODDATOS",
        "password": "MiClaveSecreta",
        "production": false
      }'
```

### 4) Respuesta exitosa

```json
{
  "success": true,
  "filename": "20600520033-01-F001-00000001.zip",
  "environment": "BETA",
  "responseCode": "0",
  "description": "La Factura numero F001-1, ha sido registrada",
  "cdr": {
    "filename": "R-20600520033-01-F001-00000001.zip",
    "contentBase64": "UEsDBAoAAAAAAAAAIQ...",
    "sizeBytes": 4823
  }
}
```

El campo `cdr.contentBase64` es el ZIP-respuesta de SUNAT con el CDR (ApplicationResponse); descodifícalo y guárdalo como `R-{nombre}`.

### 5) Respuesta con rechazo

```json
{
  "success": false,
  "filename": "20600520033-01-F001-00000001.zip",
  "environment": "BETA",
  "responseCode": "2300",
  "description": "El comprobante ya fue registrado anteriormente",
  "errorCode": "SUNAT_REJECTED",
  "errorMessage": "El comprobante ya fue registrado anteriormente",
  "cdr": { "filename": "R-...", "contentBase64": "...", "sizeBytes": 1820 }
}
```

### 6) Consulta de ticket

```bash
curl -X POST http://localhost:8085/api/v1/cpe/status \
  -H "Content-Type: application/json" \
  -d '{
        "ticket": "20170012345678901",
        "username": "20600520033MODDATOS",
        "password": "MiClaveSecreta",
        "production": false
      }'
```

```json
{
  "success": true,
  "environment": "BETA",
  "ticket": "20170012345678901",
  "statusCode": "0",
  "statusMessage": "Resumen registrado"
}
```

## Códigos de error más comunes

| `errorCode` | Significado |
|-------------|-------------|
| `INVALID_FILENAME` | El nombre del zip no respeta el formato `RUC-TIPO-SERIE-NUMERO.zip` o el tipo no es 01/03/05/06 |
| `MISSING_FILE` | No se envió el archivo |
| `MISSING_CREDENTIALS` | Username o password vacíos |
| `EMPTY_RESPONSE` | SUNAT devolvió un ZIP vacío |
| `CDR_PARSE_ERROR` | No se pudo descomprimir el ZIP-respuesta |
| `CDR_XML_PARSE_ERROR` | El XML dentro del CDR no se pudo parsear |
| `CDR_INVALID_FORMAT` | El CDR no tiene `ResponseCode`/`Description` |
| `SUNAT_REJECTED` | `responseCode != 0` (SUNAT rechazó el CPE) |
| `SOAP_FAULT` | SUNAT devolvió un SOAP Fault (credenciales, URL, etc.) |

---

## Envío de Nota de Crédito vía GRT

Cuando el operador logístico utiliza el servicio intermedio **GRT** (`restSEE`) para Notas de Crédito (tipo 07), la API expone una ruta dedicada que:

1. Recibe el XML UBL firmado por el cliente (no el zip — el zip lo arma GRT internamente).
2. Lo envía a GRT con `Content-Type: application/zip` y las credenciales como query params (`?usuario=...&contrasena=...`), exactamente como en el proyecto `back-erp`.
3. Devuelve la respuesta GRT mapeada al shape JSON `ResponseGeneraXml` (el mismo que retorna `restSEE`).

### Configuración (`application.properties`)

```properties
# Ambiente (false = beta, true = produccion)
sunat.production=false

# GRT (solo aplica a /api/v1/notacredito/send)
app.api.grt.path-enviar-betta=http://85.239.233.190:8085/restSEE/api/v2/envio/notacredito
app.api.grt.path-enviar-prd=https://api.example.com/restSEE/api/v2/envio/notacredito
app.api.grt.ruc=20559109879
app.api.grt.user=MODDATOS
app.api.grt.pass=MODDATOS
app.api.grt.connect.timeout.ms=10000
app.api.grt.request.timeout.ms=60000
```

### Ejemplo: envío vía multipart

```bash
curl -X POST http://localhost:8085/api/v1/notacredito/send \
  -F "file=@20600520033-07-F001-00000001.xml"
```

### Ejemplo: envío vía JSON + Base64

```bash
B64=$(base64 -w0 20600520033-07-F001-00000001.xml)
curl -X POST http://localhost:8085/api/v1/notacredito/send-base64 \
  -H "Content-Type: application/json" \
  -d "{
        \"filename\": \"20600520033-07-F001-00000001.xml\",
        \"fileBase64\": \"$B64\",
        \"production\": false
      }"
```

### Ejemplo: respuesta exitosa

```json
{
  "success": true,
  "filename": "20600520033-07-F001-00000001.xml",
  "environment": "BETA",
  "numeroTicket": "20260830000001",
  "resultadoPresentacion": "0",
  "codigoRespuesta": "0",
  "descripcionRespuesta": "La Nota de Credito F001-1 ha sido aceptada",
  "estadoProceso": "ACEPTADO",
  "fechaRecepcion": "2026-08-30T10:30:05",
  "valorResumen": "a1b2c3d4...",
  "codigoBarras": "|20559109879|07|F001|...|",
  "observaciones": [],
  "constanciaRecepcion": {
    "filename": "R-20600520033-07-F001-00000001.xml",
    "contentBase64": "UEsDBAoAAAAA...",
    "sizeBytes": 4823
  },
  "documentoFirmado": {
    "filename": "20600520033-07-F001-00000001.xml",
    "contentBase64": "PD94bWwgdmVyc2lvbj0..."
  },
  "firmaDigital": "MIAGCSqGSIb3..."
}
```

### Códigos de error del flujo NC/GRT

| `errorCode` | HTTP | Significado |
|-------------|------|-------------|
| `MISSING_FILE` | 400 | No se envió archivo |
| `INVALID_BASE64` | 400 | El `fileBase64` no es Base64 válido |
| `INVALID_XML` | 400 | El archivo no es un XML parseable |
| `GRT_NOT_CONFIGURED` | 500 | `app.api.grt.path-enviar-*` vacío |
| `MISSING_GRT_CREDENTIALS` | 500 | `app.api.grt.user` o `app.api.grt.pass` vacíos |
| `GRT_HTTP_ERROR` | 502 | GRT devolvió código HTTP no-200 |
| `GRT_RESPONSE_PARSE_ERROR` | 502 | GRT devolvió 200 pero el body no es JSON parseable |
| `GRT_EMPTY_BODY` | 502 | GRT devolvió 200 con body vacío |
| `NETWORK_ERROR` | 502 | Timeout, conexión rechazada, etc. |
| `SUNAT_REJECTED` | 502 | `codigoRespuesta != "0"` o `codigoExcepcion != null` |

---

## Configuración (`application.properties`)

```properties
server.port=8085
sunat.production=false
sunat.soap.connect.timeout.ms=15000
sunat.soap.request.timeout.ms=60000
spring.http.multipart.max-file-size=20480KB
```

## Compilar y ejecutar

```bash
mvn clean package -DskipTests
java -jar target/sunat-cpe-api.jar
```

o en desarrollo:

```bash
mvn spring-boot:run
```

## Diferencias con el proyecto `encargaefact`

| Tema | `encargaefact` (monolito) | `sunat-cpe-api` (este proyecto) |
|------|----------------------------|----------------------------------|
| Stack UI | Thymeleaf + JSP | API REST pura |
| Persistencia | SQL Server + MyBatis | Sin BD |
| Firma XML | Apache Santuario xmlsec | No firma, recibe zip firmado |
| Generación de PDF | iText + códigos QR/Barras | No genera |
| Generación XML UBL | Sí | No, lo entrega el cliente |
| Scheduler de envío masivo | `@Scheduled` cron | No |
| Stubs JAX-WS | `com.alo.digital.transportes...` | `pe.encarga.sunatapi.soap...` |

La lógica del envío SOAP (`BillService.sendBill`) y del parseo del CDR (`ZipInputStream` + `cbc:ResponseCode`/`cbc:Description`) está tomada directamente del `EnvioSunatService` original para mantener paridad funcional con lo que ya funciona en producción.