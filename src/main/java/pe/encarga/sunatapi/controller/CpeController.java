package pe.encarga.sunatapi.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pe.encarga.sunatapi.config.SunatProperties;
import pe.encarga.sunatapi.service.SunatSendResult;
import pe.encarga.sunatapi.service.SunatSendService;
import pe.encarga.sunatapi.service.SunatStatusResult;

/**
 * Controller de envio directo a SUNAT (BillService SOAP).
 *
 * <p>Reemplaza al intermediario GRT. Credenciales SOL via query params
 * (mismo patron que el codigo legacy de back-erp):
 * {@code ?usuario=<ruc+MODDATOS>&contrasena=<password>}.</p>
 */
@RestController
@RequestMapping("/api/v1/cpe")
public class CpeController {

    private static final Log log = LogFactory.getLog(CpeController.class);

    private final SunatSendService sunatSendService;
    private final SunatProperties sunatProperties;

    @Autowired
    public CpeController(SunatSendService sunatSendService, SunatProperties sunatProperties) {
        this.sunatSendService = sunatSendService;
        this.sunatProperties = sunatProperties;
    }

    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> send(
            @RequestPart("file") MultipartFile file,
            @RequestParam("usuario") String usuario,
            @RequestParam("contrasena") String contrasena,
            @RequestParam(value = "production", defaultValue = "false") boolean production) {

        if (file == null || file.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "MISSING_FILE",
                    "Debe adjuntar el archivo .zip firmado en el campo 'file'.");
        }

        File temp = null;
        try {
            String original = file.getOriginalFilename() == null ? "cpe.zip" : file.getOriginalFilename();
            temp = File.createTempFile("cpe-", "-" + System.currentTimeMillis() + ".zip");
            file.transferTo(temp);

            SunatSendResult result = sunatSendService.sendBill(temp, original, usuario, contrasena, production, null);
            return ResponseEntity.status(result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY)
                    .body(toJson(result));

        } catch (IOException io) {
            log.error("Error de I/O al procesar el archivo", io);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR", io.getMessage());
        } catch (Exception ex) {
            log.error("Error inesperado en /send", ex);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR",
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        } finally {
            if (temp != null && temp.exists()) {
                temp.delete();
            }
        }
    }

    @PostMapping(value = "/send-base64", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> sendBase64(@RequestBody Map<String, Object> body) {
        Object fileB64Obj = body.get("fileBase64");
        Object filenameObj = body.get("filename");
        Object usuarioObj = body.get("usuario");
        Object contrasenaObj = body.get("contrasena");
        Object productionObj = body.get("production");

        if (fileB64Obj == null || !(fileB64Obj instanceof String)) {
            return error(HttpStatus.BAD_REQUEST, "MISSING_FILE",
                    "Debe enviar el campo 'fileBase64' con el contenido del .zip en Base64.");
        }

        String filename = filenameObj == null ? "cpe.zip" : String.valueOf(filenameObj);

        File temp = null;
        try {
            byte[] zipBytes = Base64.getDecoder().decode(((String) fileB64Obj).trim());
            temp = File.createTempFile("cpe-", "-" + System.currentTimeMillis() + ".zip");
            Files.write(temp.toPath(), zipBytes);

            String usuario = usuarioObj == null ? null : String.valueOf(usuarioObj);
            String contrasena = contrasenaObj == null ? null : String.valueOf(contrasenaObj);
            boolean production = Boolean.TRUE.equals(productionObj);

            SunatSendResult result = sunatSendService.sendBill(temp, filename, usuario, contrasena, production, null);
            return ResponseEntity.status(result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY)
                    .body(toJson(result));

        } catch (IllegalArgumentException iae) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_BASE64", iae.getMessage());
        } catch (Exception ex) {
            log.error("Error inesperado en /send-base64", ex);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR",
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        } finally {
            if (temp != null && temp.exists()) {
                temp.delete();
            }
        }
    }

    @PostMapping(value = "/status", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> status(@RequestBody Map<String, Object> body) {
        String ticket = str(body.get("ticket"));
        String usuario = str(body.get("usuario"));
        String contrasena = str(body.get("contrasena"));
        boolean production = Boolean.TRUE.equals(body.get("production"));
        if (production == false && body.get("production") == null) {
            production = sunatProperties.isDefaultProduction();
        }

        SunatStatusResult result = sunatSendService.getStatus(ticket, usuario, contrasena, production);

        Map<String, Object> json = new HashMap<>();
        json.put("success", result.isSuccess());
        json.put("environment", result.getEnvironment());
        json.put("ticket", result.getTicket());
        json.put("statusCode", result.getStatusCode());
        json.put("statusMessage", result.getStatusMessage());
        if (result.getErrorCode() != null) {
            json.put("errorCode", result.getErrorCode());
        }
        if (result.getErrorMessage() != null) {
            json.put("errorMessage", result.getErrorMessage());
        }
        return ResponseEntity.status(result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(json);
    }

    @PostMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> json = new HashMap<>();
        json.put("status", "UP");
        json.put("defaultProduction", sunatProperties.isDefaultProduction());
        json.put("connectTimeoutMs", sunatProperties.getConnectTimeoutMs());
        json.put("requestTimeoutMs", sunatProperties.getRequestTimeoutMs());
        return ResponseEntity.ok(json);
    }

    private Map<String, Object> toJson(SunatSendResult r) {
        Map<String, Object> json = new HashMap<>();
        json.put("success", r.isSuccess());
        json.put("filename", r.getFilename());
        json.put("environment", r.getEnvironment());
        json.put("responseCode", r.getResponseCode());
        json.put("description", r.getDescription());
        if (r.getTicket() != null) {
            json.put("ticket", r.getTicket());
        }
        if (r.getCdrZip() != null && r.getCdrZip().length > 0) {
            Map<String, Object> cdr = new HashMap<>();
            cdr.put("filename", "R-" + r.getFilename());
            cdr.put("contentBase64", Base64.getEncoder().encodeToString(r.getCdrZip()));
            cdr.put("sizeBytes", r.getCdrZip().length);
            json.put("cdr", cdr);
        }
        if (!r.isSuccess()) {
            json.put("errorCode", r.getErrorCode());
            json.put("errorMessage", r.getErrorMessage());
        }
        return json;
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> json = new HashMap<>();
        json.put("success", false);
        json.put("errorCode", code);
        json.put("errorMessage", message);
        return ResponseEntity.status(status).body(json);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
