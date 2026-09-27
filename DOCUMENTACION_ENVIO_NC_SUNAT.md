# Envio de CPEs - Nota de Credito a SUNAT (via GRT)

Documentacion tecnica del proceso de envio de Notas de Credito Electronicas (tipo `07`) desde `back-erp` hasta SUNAT, transitando por el servicio intermedio **GRT** (`see.alo.digital`) que firma el XML con certificado digital y lo remite a SUNAT.

Alcance:

- Endpoints HTTP internos y externos.
- DTOs de request / response y payloads crudos (JSON).
- Catalogos SUNAT usados (motivo, tipo de documento, tributo).
- Maquina de estados, tablas involucradas y flujo de reintentos.
- Snippet OpenAPI 3.0.3.

Codigo fuente de referencia:

```
src/main/java/com/alo/digital/facturacion/
  entity/rest/NotaCredito/                 -- payload JSON UBL 2.1
  entity/rest/response/ResponseGeneraXML   -- respuesta GRT
  request/                                 -- request DTOs internos
  service/impl/GeneratorNotaCreditoMasiveServiceImpl.java
  service/impl/RequestApiSunatNotaCreditoSunatImpl.java
  rest/invoices/GenerarNotaCreditoIndividualService.java
  rest/invoices/SenderSunatTributariosController.java
  enumeration/EstadoNotaCreditoEnum.java
  enumeration/MotivoNotaCreditoEnum.java
src/main/resources/Empresas/<empresa>/application.properties
```

---

## 1. Resumen del flujo

back-erp **no llama directamente a SUNAT**. El envio se delega en un servicio intermedio (**GRT**) en dos pasos:

```
back-erp                                GRT (restSEE)                            SUNAT
--------                                -----------                              -----
Paso 1 - POST /v2/proceso/notacredito  ->  Firma XML con cert. digital          ->  (no aplica)
         (JSON: objeto NotaCredito)         y devuelve los bytes firmados
         <- ResponseGeneraXML
            (documentoFirmado base64)

Paso 2 - POST /v2/envio/notacredito   ->  Empaqueta ZIP y envia                 ->  Valida y emite CDR
         (body: bytes del XML firmado)                                              + codigo de barras
         <- ResponseGeneraXML                                                          + hash
            (constanciaRecepcion base64, codigoRespuesta, ...)
```

| Fase | Endpoint GRT | Content-Type | Body | Salida clave |
|------|--------------|--------------|------|--------------|
| Generacion | `POST /v2/proceso/notacredito` | `application/json` | `NotaCredito` (JSON) | `ResponseGeneraXML.documentoFirmado` |
| Envio | `POST /v2/envio/notacredito` | `application/zip` | `byte[]` (XML firmado) | `ResponseGeneraXML` con CDR / ticket / codigos |

El envio real a SUNAT ocurre en la **Fase 2**. La Fase 1 deja el XML firmado persistido en `v_notasdecreditogenerado.documentoFirmado` para que el scheduler externo (`service-erp`) o un endpoint manual lo envie despues.

---

## 2. Endpoints

### 2.1 Endpoints internos (back-erp)

Prefijo: `sunat-tributarios` (definido en `SenderSunatTributariosController.java:42`).

| Metodo | Ruta | Body | Uso |
|--------|------|------|-----|
| `POST` | `/sunat-tributarios/send/invoice` | `RqSolicitudInvoiceBinario` | Envio individual de una NC a SUNAT (Fase 2). |
| `POST` | `/sunat-tributarios/send/invoice/all` | `RqSolicitudEnvioGrts` | Encolar todas las NC de una fecha en `POR_ENVIAR`. |
| `POST` | `/sunat-tributarios/state/invoices/back` | `RqSolicitudEnvioGrts` | Revierte NC `EXCEPTION` / `REINTENTOS_SUPERADOS` a `POR_ENVIAR` para reintento. |
| `GET`  | `/sunat-tributarios/state/invoices` | query params | Monitoreo de CPEs por fecha y estado. |

Detalle del handler `POST /send/invoice` para `tipoDocumento = "07"` (NC):

```
SenderSunatTributariosController.sendSunat(...)
  case "07":
    1) vNotaCredito         = vNotaCreditoRepository.findById(nroInvoice)
       vNotaCreditoGenerado = vNotaCreditoGeneradoRepository.findByNroNotaCredito(nroInvoice + "")
       si falta alguno -> 404 "Nota de Credito no encontrada"
    2) vNotaCredito.enviado = EN_PROCESO (1)
       vNotaCreditoGenerado.fechaRecepcion = LocalDateTime.now()
    3) responseGeneraXML = iConsumeRestApiSunatNotaCredito.envioSunat(vNotaCreditoGenerado.documentoFirmado)
    4) Evaluar responseGeneraXML:
         codigoRespuesta == "0" && codigoExcepcion == null -> ACEPTADO (2)
         codigoExcepcion != null                          -> EXCEPTION (3)
         resto                                            -> EXCEPTION (3)
    5) Actualizar v_notasdecredito + v_notasdecreditogenerado con la respuesta
    6) actualizarSisColaNotaCredito(nro, responseGeneraXML)
       intento++ ; estado = ACEPTADO / EXCEPTION segun corresponda
    7) 200 OK "Nota de Credito Enviada Correctamente"
```

### 2.2 Endpoints externos (GRT)

Configuracion en `application.properties` (perfil por empresa, en `src/main/resources/Empresas/<empresa>/`):

```properties
app.api.ruc.grt=20559109879
app.api.user.grt=MODDATOS
app.api.pass.grt=MODDATOS

# Fase 1: generar XML firmado
app.api.path.grt.notacredito.generar=http://85.239.233.190:8085/restSEE/api/v2/proceso/notacredito

# Fase 2: enviar a SUNAT
app.api.path.grt.notacredito.enviar=http://85.239.233.190:8085/restSEE/api/v2/envio/notacredito
```

**Autenticacion:** query params en cada request.

```
?usuario={app.api.ruc.grt}&contrasena={app.api.pass.grt}
```

> Nota: el codigo para NC usa el literal `contrasena` (sin enie) en el query string -- ver `RequestApiSunatNotaCreditoSunatImpl.java:52,108`.

---

## 3. DTOs y payloads

### 3.1 Request interno - `RqSolicitudInvoiceBinario`

`request/RqSolicitudInvoiceBinario.java`. Body de `POST /send/invoice`.

```json
{
  "nroInvoice":      123,
  "tipoDocumento":   "07",
  "tipoServicio":    "E",
  "nombreDocumento": null
}
```

| Campo | Tipo | Notas |
|-------|------|-------|
| `nroInvoice` | Integer | PK de `v_notasdecredito` (campo `nro`). |
| `tipoDocumento` | String | `"07"` para NC. |
| `tipoServicio` | String | `"E"` encomienda, `"C"` canje, `"N"` normal. |
| `nombreDocumento` | String | Opcional. |

### 3.2 Request interno - `RqSolicitudEnvioGrts`

`request/RqSolicitudEnvioGrts.java`. Body de `POST /send/invoice/all` y `POST /state/invoices/back`.

```json
{
  "fechaemision": "2026-08-30"
}
```

| Campo | Tipo | Notas |
|-------|------|-------|
| `fechaemision` | String (ISO LocalDate) | Fecha de emision del CPE (formato `yyyy-MM-dd`). |

### 3.3 Payload JSON - `NotaCredito` (Fase 1)

`entity/rest/NotaCredito/NotaCredito.java`. UBL 2.1 simplificado. Se serializa con Gson (`setPrettyPrinting()`) en `GeneratorNotaCreditoMasiveServiceImpl.java:61`.

```json
{
  "codigoTipoDocumento": "07",
  "numeroSerie": "F001",
  "numeroCorrelativo": "00000001",
  "fechaEmision": "2026-08-30T10:30:00.0000000-05:00",
  "horaEmision":  "2026-08-30T10:30:00.0000000-05:00",
  "montoValorVentaMonedaOriginal": 50.00,
  "montoImpuestoMonedaOriginal":   9.00,
  "montoRedondeoTotalMonedaOriginal": null,
  "montoTotalMonedaOriginal":     59.00,
  "codigoTipoMoneda": "PEN",
  "tamanioPapel": null,

  "oEmisor": {
    "codigoTipoDocumentoIdentidad": "6",
    "numeroDocumentoIdentidad": "20559109879",
    "apellidosNombresDenominacionRazonSocial": "CRUZ CARGO S.A.C.",
    "nombreComercial": null,
    "codigoPais": "PE",
    "codigoUbicacionGeografica": "150101",
    "codigoEstablecimientoAnexo": "0001",
    "departamento": "LIMA",
    "provincia":    "LIMA",
    "distrito":     "LIMA",
    "urbanizacion": "-",
    "direccion":    "AV. EJEMPLO 123",
    "direccionEstablecimientoAnexo": null
  },

  "oAdquiriente": {
    "codigoTipoDocumentoIdentidad": "6",
    "descripcionTipoDocumentoIdentidad": "RUC",
    "numeroDocumentoIdentidad": "20123456789",
    "apellidosNombresDenominacionRazonSocial": "CLIENTE S.A.",
    "codigoPais": "PE",
    "codigoUbicacionGeografica": null,
    "departamento": null,
    "provincia":    null,
    "distrito":     null,
    "urbanizacion": null,
    "direccion":    "AV. CLIENTE 456"
  },

  "lDetalleNotaCredito": [
    {
      "numeroOrden": 1,
      "cantidad": 1.0,
      "montoValorVentaUnitarioMonedaOriginal": 50.00,
      "montoPrecioVentaUnitarioMonedaOriginal": 59.00,
      "montoValorReferencialUnitarioMonedaOriginal": null,
      "montoValorVentaTotalMonedaOriginal": 50.00,
      "montoTotalMonedaOriginal": 59.00,
      "oNotaCredito": null,
      "oProducto": {
        "codigo": null,
        "descripcion": "SERVICIO DE TRANSPORTE",
        "codigoUnidadMedida": "NIU",
        "codigoSunat": null
      },
      "lImpuesto": [
        {
          "codigo": "1000",
          "nombre": "IGV",
          "codigoInternacional": "VAT",
          "montoBaseMonedaOriginal": 50.00,
          "porcentaje": 18.00,
          "codigoTipoAfectacionIGV": "10",
          "montoTotalMonedaOriginal": 9.00
        }
      ],
      "lPropiedadItemAdicional": null
    }
  ],

  "lImpuesto": [
    {
      "codigo": "1000",
      "nombre": "IGV",
      "codigoInternacional": "VAT",
      "montoBaseMonedaOriginal": 50.00,
      "porcentaje": 18.00,
      "codigoTipoAfectacionIGV": null,
      "montoTotalMonedaOriginal": 9.00
    }
  ],

  "lDocumentoReferenciaEnvio": [],
  "lDocumentoReferenciaAdicional": null,

  "lComprobanteModificado": [
    {
      "codigoTipoDocumento": "01",
      "numeroSerie": "F002",
      "numeroCorrelativo": "00000001",
      "fechaEmision": null,
      "codigoTipoNota": "01",
      "lMotivoSustento": [
        "Anulacion de la operacion"
      ]
    }
  ],

  "lNota": [
    {
      "codigo": "1000",
      "descripcion": "CINCUENTA Y NUEVE Y 00/100 SOLES"
    }
  ],

  "parametrosAdicionalesReporte": null
}
```

Mapeo de campos JSON -> clases Java:

| JSON | Clase | Archivo |
|------|-------|---------|
| raiz | `NotaCredito` | `entity/rest/NotaCredito/NotaCredito.java` |
| `oEmisor` | `OEmisor` | `entity/rest/NotaCredito/OEmisor.java` |
| `oAdquiriente` | `OAdquiriente` | `entity/rest/NotaCredito/OAdquiriente.java` |
| `lDetalleNotaCredito[]` | `LDetalleNotaCredito` | `entity/rest/NotaCredito/LDetalleNotaCredito.java` |
| `oProducto` | `OProducto` | `entity/rest/NotaCredito/OProducto.java` |
| `lImpuesto[]` | `LImpuesto` | `entity/rest/NotaCredito/LImpuesto.java` |
| `lComprobanteModificado[]` | `LComprobanteModificado` | `entity/rest/NotaCredito/LComprobanteModificado.java` |
| `lNota[]` | `LNotum` | `entity/rest/NotaCredito/LNotum.java` |

### 3.4 Payload binario - Fase 2

`POST /v2/envio/notacredito`. No es JSON. Se envia el XML firmado como `byte[]` con `Content-Type: application/zip`.

| Header | Valor |
|--------|-------|
| `Content-Type` | `application/zip` |
| `Accept` | (default) |

| Parametro URL | Valor |
|---------------|-------|
| `usuario` | `app.api.ruc.grt` |
| `contrasena` | `app.api.pass.grt` |

Body: bytes del XML firmado, decodificados desde Base64 (`V_NotaCreditoGenerado.documentoFirmado`). El nombre del archivo ZIP y la estructura interna del paquete los determina GRT; back-erp solo manda los bytes.

Codigo relevante:

```java
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.valueOf("application/zip"));
HttpEntity<byte[]> requestEntity = new HttpEntity<>(documento, headers);
ResponseEntity<ResponseGeneraXML> response = restTemplate.exchange(
    url, HttpMethod.POST, requestEntity, ResponseGeneraXML.class);
```

### 3.5 Response GRT - `ResponseGeneraXML`

`entity/rest/response/ResponseGeneraXML.java`.

```json
{
  "numeroTicket": "20260830000001",
  "resultadoPresentacion": "0",
  "codigoRespuesta": "0",
  "descripcionRespuesta": "La Nota de Credito numerada F001-00000001 ha sido aceptada",
  "constanciaRecepcion": "UEsDBAoAAAAA...",
  "descripcionExcepcion": null,
  "descripcionMensaje": "Documento Generado",
  "documentoFirmado": "PD94bWwgdmVyc2lvbj0...",
  "codigoExcepcion": null,
  "representacionImpresa": "JVBERi0xLjQKJe...",
  "fechaRecepcion": "2026-08-30T10:30:05",
  "valorResumen": "a1b2c3d4...",
  "codigoBarras": "|20559109879|07|F001|...|",
  "estadoProceso": "ACEPTADO",
  "firmaDigital": "MIAGCSqGSIb3DQEHAqCAMIACAQEx...",
  "codigoMensaje": "0",
  "observaciones": []
}
```

| Campo | Tipo | Notas |
|-------|------|-------|
| `numeroTicket` | String | Ticket devuelto por SUNAT/GRT. |
| `resultadoPresentacion` | String | `"0"` = OK. |
| `codigoRespuesta` | String | `"0"` = OK; cualquier otro valor = error. |
| `descripcionRespuesta` | String | Mensaje legible de SUNAT. |
| `constanciaRecepcion` | String (base64) | CDR (ZIP con XML firmado + hash). Solo presente si la operacion fue aceptada. |
| `descripcionExcepcion` | String | Mensaje de excepcion cuando `codigoExcepcion != null`. |
| `descripcionMensaje` | String | Mensaje interno (ej. `"Documento Generado"`). |
| `documentoFirmado` | String (base64) | XML firmado. Solo presente en Fase 1. |
| `codigoExcepcion` | String | Codigo de excepcion cuando hay error de negocio. |
| `representacionImpresa` | String (base64) | PDF del CPE (opcional). |
| `fechaRecepcion` | String (ISO LocalDateTime) | Fecha/hora de recepcion por SUNAT. |
| `valorResumen` | String | Hash SHA-256 del XML firmado (alimenta el QR). |
| `codigoBarras` | String | Payload del codigo de barras / QR. |
| `estadoProceso` | String | Estado del proceso (`"ACEPTADO"`, etc.). |
| `firmaDigital` | String | Firma digital aplicada al XML. |
| `codigoMensaje` | String | Codigo de mensaje interno. |
| `observaciones` | List<String> | Observaciones adicionales de SUNAT. |

### 3.6 Response interno al cliente - `Response<?>`

Wrapper generico del proyecto (`utilitario/UtilGenerico.java`). Para envio de NC:

```json
{
  "out_estado":  200,
  "out_mensaje": "Nota de Credito Enviada Correctamente",
  "out_detalle": "Comprobante enviado correctamente",
  "out_data":    ""
}
```

Casos:

| Caso | out_estado | out_mensaje |
|------|-----------|-------------|
| NC no encontrada en BD | 404 | `"Nota de Credito no encontrada"` |
| Excepcion HTTP / WS | 500 | `"Error en el proceso de envio de NC"` |
| Envio OK | 200 | `"Nota de Credito Enviada Correctamente"` |

---

## 4. Implementacion HTTP del cliente GRT

### 4.1 Clase cliente

`service/impl/RequestApiSunatNotaCreditoSunatImpl.java`. Implementa `IConsumerRestApiSunat` y se inyecta con el qualifier `requestApiSunatNotaCreditoSunatImpl`.

### 4.2 Fase 1 - `generateXML(Object obj)` (lineas 51-100)

Usa `HttpURLConnection` raw.

```java
URL url = new URL(PATH_GENERAR + "?usuario=" + ruc + "&contrasena=" + contrasenia);
HttpURLConnection connection = (HttpURLConnection) url.openConnection();
connection.setRequestMethod("POST");
connection.setRequestProperty("Content-Type", "application/json");
connection.setRequestProperty("Accept",       "application/json");
connection.setDoOutput(true);

String requestBody = gson.toJson((NotaCredito) obj);

try (OutputStream os = connection.getOutputStream()) {
    os.write(requestBody.getBytes(StandardCharsets.UTF_8));
}

int statusCode = connection.getResponseCode();
if (statusCode == HttpURLConnection.HTTP_OK) {
    // parsear body -> ResponseGeneraXML
} else {
    log.error("Error en la llamada a la API: {}", statusCode);
    // leer errorStream y loguearlo
    return null;
}
```

### 4.3 Fase 2 - `envioSunat(byte[] documento)` (lineas 107-129)

Usa `RestTemplate` de Spring.

```java
String url = PATH_ENVIAR + "?usuario=" + ruc + "&contrasena=" + contrasenia;

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.valueOf("application/zip"));

try {
    HttpEntity<byte[]> requestEntity = new HttpEntity<>(documento, headers);
    ResponseEntity<ResponseGeneraXML> response = restTemplate.exchange(
        url, HttpMethod.POST, requestEntity, ResponseGeneraXML.class);

    if (response.getStatusCode() == HttpStatus.OK) {
        return response.getBody();
    } else {
        log.error("Error en el envio: {}", response.getStatusCode());
        return null;
    }
} catch (Exception e) {
    log.error("Error en la solicitud: {}", e.getMessage());
    throw e;
}
```

### 4.4 Manejo de respuesta (`SenderSunatTributariosController.java:186-265`)

| Caso | Condicion | `v_notasdecredito.enviado` | Campos actualizados |
|------|-----------|----------------------------|---------------------|
| Aceptado | `codigoRespuesta == "0" && codigoExcepcion == null` | `ACEPTADO (2)` | `constanciaRecepcion` (CDR), `codigoBarras`, `observaciones`, `estadoProceso`, `descripcionMensaje`, `respuestaSunat` |
| Excepcion SUNAT | `codigoExcepcion != null` | `EXCEPTION (3)` | `descripcionExcepcion`, `codigoRespuesta`, `descripcionRespuesta`, `constanciaRecepcion` |
| Otros | sin codigo 0 ni excepcion clara | `EXCEPTION (3)` | `descripcionRespuesta` o mensaje generico |

> `consultarCdrByTicket` esta **no implementada** para NC (`RequestApiSunatNotaCreditoSunatImpl.java:131-135`).

---

## 5. Catalogos SUNAT

### 5.1 Tipo de documento (`codigoTipoDocumento`)

| Codigo | Significado |
|--------|-------------|
| `01` | Factura |
| `03` | Boleta |
| `07` | **Nota de Credito** |
| `08` | Nota de Debito |

### 5.2 Tipo de documento del comprobante modificado (`codigoTipoDocumento` en `lComprobanteModificado`)

| Codigo | Significado |
|--------|-------------|
| `01` | Factura |
| `03` | Boleta |

### 5.3 Motivo de la NC (`codigoTipoNota`) - Catalogo 09 SUNAT

Fuente: `enumeration/MotivoNotaCreditoEnum.java`.

| Codigo | Descripcion |
|--------|-------------|
| `01` | Anulacion de la operacion |
| `02` | Anulacion por error en el RUC |
| `03` | Correccion por error en la descripcion |
| `04` | Descuento global |
| `05` | Descuento por item |
| `06` | Devolucion total |
| `07` | Devolucion por item |
| `08` | Bonificacion |
| `09` | Disminucion en el valor |
| `10` | Otros conceptos |
| `11` | Ajustes de operaciones de exportacion |
| `13` | Ajustes - montos y/o fechas de pago |

### 5.4 Tipo de documento de identidad del adquiriente

| Codigo | Significado |
|--------|-------------|
| `0` | Sin documento (venta menor) |
| `1` | DNI |
| `6` | RUC |

### 5.5 Codigo de tributo

| Codigo | Significado | Porcentaje | Caso |
|--------|-------------|------------|------|
| `1000` | IGV | `18.00` | Servicio gravado (encomienda, canje, normal). |
| `9997` | EXO | `0.00` | Servicio exonerado. |

Asignacion en `GeneratorNotaCreditoMasiveServiceImpl.tributoCondicionubl21()`:

```java
case "B" -> 9997 EXO 0.00
case "E"|"C"|"N"|default -> 1000 IGV 18.00
```

### 5.6 Codigo de afectacion IGV (`codigoTipoAfectacionIGV`)

| Codigo | Significado |
|--------|-------------|
| `10` | Gravado - Operacion onerosa |
| `20` | Exonerado - Operacion onerosa |
| `30` | Inafecto - Operacion onerosa |
| `40` | Exportacion |

### 5.7 Codigo de moneda

| Codigo | Significado |
|--------|-------------|
| `PEN` | Sol |
| `USD` | Dolar americano |

---

## 6. Estados del comprobante

`enumeration/EstadoNotaCreditoEnum.java`.

```java
POR_ENVIAR           (0, "Por enviar / Por generar")
EN_PROCESO           (1, "En proceso")
ACEPTADO             (2, "Aceptado por SUNAT")
EXCEPTION            (3, "Excepcion (reintentable)")
REINTENTOS_SUPERADOS (4, "Reintentos superados")
```

Persistencia del mismo codigo en 3 tablas:

| Tabla | Columna | Comentario |
|-------|---------|------------|
| `v_notasdecredito` | `enviado` | estado vivo de la NC |
| `v_solicitudnotacredito` | `estado` | cola de **generacion** (XML) |
| `sis_cola_nota_credito` | `estado` | cola de **envio** (SUNAT) |

Configuracion de reintentos:

```properties
app.limite.reintento.consulta.ticket.grt=10
```

Una vez que `v_solicitudnotacredito.intento` alcanza este limite, la NC pasa a `REINTENTOS_SUPERADOS`.

Diagrama de transiciones:

```
        generarXML_PDF OK                          envioSunat OK
        ------------------                          --------------
nuevo  ───────────────►  POR_ENVIAR (0)  ──────────────►  EN_PROCESO (1)  ──────►  ACEPTADO (2)
                                          send/invoice/all
                                          (encolar)
                                          │
                                          │  generar ERROR o
                                          │  envio EXCEPTION
                                          ▼
                                      EXCEPTION (3)
                                          │
                                          │ intento++ (en cada envio fallido)
                                          │ llega a app.limite.reintento.consulta.ticket.grt (10)
                                          ▼
                              REINTENTOS_SUPERADOS (4)
                                          │
                                          │  POST /state/invoices/back
                                          ▼
                                    POR_ENVIAR (0)
```

---

## 7. Tablas involucradas

| Tabla | Java entity | Rol |
|-------|-------------|-----|
| `b_notascredito` | `B_NotasCredito` | Cabecera de la NC registrada por el backoffice. Input del usuario. |
| `b_notascreditodetalle` | `B_NotasCreditoDetalle` | Detalle de la NC. |
| `v_notasdecredito` | `V_NotaCredito` | NC "procesada": JSON UBL persistido en `trama_json`, estado `enviado`, datos del comprobante modificado. |
| `v_notasdecreditogenerado` | `V_NotaCreditoGenerado` | XML firmado (`documentoFirmado`), CDR (`constanciaRecepcion`), hash (`valorResumen`), codigo de barras, PDF (`representacionImpresa`). |
| `v_solicitudnotacredito` | `V_SolicitudNotaCredito` | Cola de **generacion** (reintentos de la Fase 1). |
| `sis_cola_nota_credito` | `SisColaNotaCredito` | Cola de **envio** consumida por el scheduler externo. |

---

## 8. Diagrama de secuencia (envio individual)

```
Cliente        back-erp Controller    back-erp ServiceImpl         GRT                SUNAT
  │                    │                        │                    │                   │
  │ POST /send/        │                        │                    │                   │
  │  invoice           │                        │                    │                   │
  │ (nroInvoice,       │                        │                    │                   │
  │  tipoDoc="07")     │                        │                    │                   │
  │───────────────────►│                        │                    │                   │
  │                    │ findById(nro)          │                    │                   │
  │                    │ findByNroNotaCredito    │                    │                   │
  │                    │ -> V_NotaCredito +     │                    │                   │
  │                    │    V_NotaCreditoGenerado                   │                   │
  │                    │                        │                    │                   │
  │                    │ setEnviado(1)          │                    │                   │
  │                    │                        │                    │                   │
  │                    │ envioSunat(bytes)      │                    │                   │
  │                    │───────────────────────►│                    │                   │
  │                    │                        │ POST /v2/envio/    │                   │
  │                    │                        │  notacredito       │                   │
  │                    │                        │  body=bytes        │                   │
  │                    │                        │  CT: zip           │                   │
  │                    │                        │───────────────────►│                   │
  │                    │                        │                    │ POST XML firmado  │
  │                    │                        │                    │──────────────────►│
  │                    │                        │                    │                   │
  │                    │                        │                    │◄──────────────────│
  │                    │                        │                    │  CDR              │
  │                    │                        │◄───────────────────│                   │
  │                    │                        │ ResponseGeneraXML  │                   │
  │                    │                        │ (CDR, codigoRespuesta)                 │
  │                    │ actualizar entidades   │                    │                   │
  │                    │ (enviado, CDR, etc.)   │                    │                   │
  │                    │ actualizar cola        │                    │                   │
  │◄───────────────────│                        │                    │                   │
  │ 200 OK             │                        │                    │                   │
```

---

## 9. Ejemplos curl

### 9.1 Envio individual

```bash
curl -X POST http://localhost:8091/sunat-tributarios/send/invoice \
  -H "Content-Type: application/json" \
  -d '{
    "nroInvoice": 123,
    "tipoDocumento": "07",
    "tipoServicio": "E"
  }'
```

### 9.2 Envio masivo (encolar)

```bash
curl -X POST http://localhost:8091/sunat-tributarios/send/invoice/all \
  -H "Content-Type: application/json" \
  -d '{ "fechaemision": "2026-08-30" }'
```

### 9.3 Reversion para reintento

```bash
curl -X POST http://localhost:8091/sunat-tributarios/state/invoices/back \
  -H "Content-Type: application/json" \
  -d '{ "fechaemision": "2026-08-30" }'
```

### 9.4 Monitoreo

```bash
curl "http://localhost:8091/sunat-tributarios/state/invoices?fecha=2026-08-30&estado=2&page=0&size=10"
```

| Param | Tipo | Notas |
|-------|------|-------|
| `fecha` | String (yyyy-MM-dd) | Fecha de emision. |
| `estado` | Integer | Codigo de `EstadoNotaCreditoEnum` (`0`, `1`, `2`, `3`, `4`). |
| `page` | Integer (default `0`) | Pagina. |
| `size` | Integer (default `5`) | Tamano de pagina. |

---

## 10. Snippet OpenAPI 3.0.3

Compatible con SpringDoc (`springdoc.swagger-ui.path=/swagger-ui.html`).

```yaml
openapi: 3.0.3
info:
  title: back-erp - Envio de NC a SUNAT
  version: 1.0.0
  description: Endpoints para la operacion de envio de Notas de Credito (07) a SUNAT.

paths:
  /sunat-tributarios/send/invoice:
    post:
      summary: Envia una NC a SUNAT (Fase 2).
      tags: [NotaCredito]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RqSolicitudInvoiceBinario'
      responses:
        '200':
          description: NC enviada correctamente.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Response'
        '404':
          description: NC no encontrada.
        '500':
          description: Error en el proceso de envio.

  /sunat-tributarios/send/invoice/all:
    post:
      summary: Encola todas las NC de una fecha en estado POR_ENVIAR.
      tags: [NotaCredito]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RqSolicitudEnvioGrts'
      responses:
        '200':
          description: Comprobantes en cola de envio.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Response'

  /sunat-tributarios/state/invoices/back:
    post:
      summary: Revierte NC con EXCEPTION o REINTENTOS_SUPERADOS a POR_ENVIAR.
      tags: [NotaCredito]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RqSolicitudEnvioGrts'
      responses:
        '200':
          description: Comprobantes listos para reenviar.

  /sunat-tributarios/state/invoices:
    get:
      summary: Lista CPEs por fecha y estado (paginado).
      tags: [NotaCredito]
      parameters:
        - in: query
          name: fecha
          required: true
          schema:
            type: string
            format: date
        - in: query
          name: estado
          required: true
          schema:
            type: integer
        - in: query
          name: page
          schema:
            type: integer
            default: 0
        - in: query
          name: size
          schema:
            type: integer
            default: 5
      responses:
        '200':
          description: Listado correcto.
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Response'
                  - type: object
                    properties:
                      out_data:
                        type: array
                        items:
                          $ref: '#/components/schemas/InvoiceSendSunatDto'

components:
  schemas:
    RqSolicitudInvoiceBinario:
      type: object
      required: [nroInvoice, tipoDocumento]
      properties:
        nroInvoice:
          type: integer
          example: 123
        tipoDocumento:
          type: string
          example: "07"
        tipoServicio:
          type: string
          example: "E"
        nombreDocumento:
          type: string
          nullable: true

    RqSolicitudEnvioGrts:
      type: object
      required: [fechaemision]
      properties:
        fechaemision:
          type: string
          format: date
          example: "2026-08-30"

    ResponseGeneraXML:
      type: object
      properties:
        numeroTicket:
          type: string
        resultadoPresentacion:
          type: string
        codigoRespuesta:
          type: string
          description: "0 = OK"
        descripcionRespuesta:
          type: string
        constanciaRecepcion:
          type: string
          format: byte
          description: Base64 del CDR.
        descripcionExcepcion:
          type: string
          nullable: true
        descripcionMensaje:
          type: string
        documentoFirmado:
          type: string
          format: byte
          description: Base64 del XML firmado (Fase 1).
        codigoExcepcion:
          type: string
          nullable: true
        representacionImpresa:
          type: string
          format: byte
          description: Base64 del PDF.
        fechaRecepcion:
          type: string
          format: date-time
        valorResumen:
          type: string
        codigoBarras:
          type: string
        estadoProceso:
          type: string
        firmaDigital:
          type: string
        codigoMensaje:
          type: string
        observaciones:
          type: array
          items:
            type: string

    Response:
      type: object
      properties:
        out_estado:
          type: integer
        out_mensaje:
          type: string
        out_detalle:
          type: string
        out_data:
          nullable: true

    InvoiceSendSunatDto:
      type: object
      properties:
        nroInvoice:
          type: integer
        nroEncomienda:
          type: integer
        tipoDocumento:
          type: string
          example: "07"
        servicio:
          type: string
        serieNumeroInvoice:
          type: string
        respuestaGeneracion:
          type: string
        respuestaSunat:
          type: string
        ticket:
          type: string
        sunatState:
          type: integer
        xmlGenerado:
          type: boolean
        sunatStateD:
          type: string
```

---

## 11. Archivos fuente de referencia

```
src/main/java/com/alo/digital/facturacion/
  entity/rest/NotaCredito/
    NotaCredito.java                                  -- Payload JSON (Fase 1)
    OEmisor.java                                      -- bloque emisor
    OAdquiriente.java                                 -- bloque adquiriente
    LDetalleNotaCredito.java                          -- item
    OProducto.java                                    -- producto del item
    LImpuesto.java                                    -- impuestos (item y totales)
    LComprobanteModificado.java                       -- referencia (clave de la NC)
    LNotum.java                                       -- monto en letras
  entity/rest/response/
    ResponseGeneraXML.java                            -- respuesta GRT
  request/
    RqSolicitudInvoiceBinario.java                    -- body envio individual
    RqSolicitudEnvioGrts.java                         -- body envio masivo / back
  dto/
    SolicitudInvoiceIndividualDto.java                -- body generacion individual
    EncomiendaFacturaDto.java                         -- proyeccion BD -> JSON UBL
    InvoiceSendSunatDto.java                          -- response listado /state/invoices
  service/
    IConsumerRestApiSunat.java                        -- interfaz comun
  service/impl/
    GeneratorNotaCreditoMasiveServiceImpl.java        -- orquesta Fase 1
    RequestApiSunatNotaCreditoSunatImpl.java          -- cliente HTTP GRT
  rest/invoices/
    GenerarNotaCreditoIndividualService.java          -- POST generacion individual
    SenderSunatTributariosController.java             -- POST /send/invoice (case "07")
                                                     -- POST /send/invoice/all
                                                     -- POST /state/invoices/back
                                                     -- GET  /state/invoices
  enumeration/
    EstadoNotaCreditoEnum.java                        -- maquina de estados NC
    MotivoNotaCreditoEnum.java                        -- catalogo 09 SUNAT
src/main/resources/Empresas/<empresa>/application.properties
                                                     -- URLs y credenciales GRT
```

---

## 12. Notas operativas

- El envio real a SUNAT depende del scheduler externo que consume `sis_cola_nota_credito` o del endpoint manual `POST /sunat-tributarios/send/invoice`.
- El XML firmado persiste en `v_notasdecreditogenerado.documentoFirmado` (columna binaria). Mientras ese campo este vacio, el envio fallara con 404.
- La consulta de CDR por ticket (`consultarCdrByTicket`) **no esta implementada** para NC.
- Para Nota de Debito (`tipoDocumento = "08"`) el flujo es analogo, usando `RequestApiSunatNotaDebitoSunatImpl` y las propiedades `app.api.path.grt.notadebito.generar` / `app.api.path.grt.notadebito.enviar`.
