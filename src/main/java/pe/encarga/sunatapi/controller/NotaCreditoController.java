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
import org.springframework.beans.factory.annotation.Value;
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

import pe.encarga.sunatapi.service.NotaCreditoSendResult;
import pe.encarga.sunatapi.service.NotaCreditoSendService;

@RestController
@RequestMapping("/api/v1/notacredito")
public class NotaCreditoController {

    private static final Log log = LogFactory.getLog(NotaCreditoController.class);

    private final NotaCreditoSendService sendService;
    private final boolean defaultProduction;

    @Autowired
    public NotaCreditoController(NotaCreditoSendService sendService,
                                 @Value("${sunat.production:false}") boolean defaultProduction) {
        this.sendService = sendService;
        this.defaultProduction = defaultProduction;
    }

    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> send(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "production", required = false) Boolean production) {

        if (file == null || file.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "MISSING_FILE",
                    "Debe adjuntar el archivo XML firmado en el campo 'file'.");
        }

        File temp = null;
        try {
            String original = file.getOriginalFilename() == null ? "nota-credito.xml"
                    : file.getOriginalFilename();
            temp = File.createTempFile("nc-", "-" + System.currentTimeMillis() + ".xml");
            file.transferTo(temp);

            byte[] bytes = Files.readAllBytes(temp.toPath());
            NotaCreditoSendResult result = sendService.send(bytes, original, resolveProduction(production));
            return ResponseEntity.status(result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY)
                    .body(toJson(result));

        } catch (IOException io) {
            log.error("Error de I/O en /notacredito/send", io);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR", io.getMessage());
        } catch (Exception ex) {
            log.error("Error inesperado en /notacredito/send", ex);
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
        Object productionObj = body.get("production");

        if (fileB64Obj == null || !(fileB64Obj instanceof String)) {
            return error(HttpStatus.BAD_REQUEST, "MISSING_FILE",
                    "Debe enviar el campo 'fileBase64' con el contenido del XML firmado en Base64.");
        }

        String filename = filenameObj == null ? "nota-credito.xml" : String.valueOf(filenameObj);
        boolean production = productionObj == null ? defaultProduction
                : (productionObj instanceof Boolean ? (Boolean) productionObj : Boolean.parseBoolean(String.valueOf(productionObj)));

        File temp = null;
        try {
            byte[] bytes = Base64.getDecoder().decode(((String) fileB64Obj).trim());
            temp = File.createTempFile("nc-", "-" + System.currentTimeMillis() + ".xml");
            Files.write(temp.toPath(), bytes);

            NotaCreditoSendResult result = sendService.send(bytes, filename, production);
            return ResponseEntity.status(result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY)
                    .body(toJson(result));

        } catch (IllegalArgumentException iae) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_BASE64", iae.getMessage());
        } catch (Exception ex) {
            log.error("Error inesperado en /notacredito/send-base64", ex);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR",
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        } finally {
            if (temp != null && temp.exists()) {
                temp.delete();
            }
        }
    }

    private boolean resolveProduction(Boolean override) {
        return override == null ? defaultProduction : override;
    }

    private Map<String, Object> toJson(NotaCreditoSendResult r) {
        Map<String, Object> json = new HashMap<>();
        json.put("success", r.isSuccess());
        json.put("filename", r.getFilename());
        json.put("environment", r.getEnvironment());

        putIfNotNull(json, "numeroTicket", r.getNumeroTicket());
        putIfNotNull(json, "resultadoPresentacion", r.getResultadoPresentacion());
        putIfNotNull(json, "codigoRespuesta", r.getCodigoRespuesta());
        putIfNotNull(json, "descripcionRespuesta", r.getDescripcionRespuesta());
        putIfNotNull(json, "codigoExcepcion", r.getCodigoExcepcion());
        putIfNotNull(json, "descripcionExcepcion", r.getDescripcionExcepcion());
        putIfNotNull(json, "descripcionMensaje", r.getDescripcionMensaje());
        putIfNotNull(json, "estadoProceso", r.getEstadoProceso());
        putIfNotNull(json, "fechaRecepcion", r.getFechaRecepcion());
        putIfNotNull(json, "valorResumen", r.getValorResumen());
        putIfNotNull(json, "codigoBarras", r.getCodigoBarras());
        putIfNotNull(json, "codigoMensaje", r.getCodigoMensaje());
        putIfNotNull(json, "firmaDigital", r.getFirmaDigital());
        if (r.getObservaciones() != null) {
            json.put("observaciones", r.getObservaciones());
        }
        putBase64IfNotNull(json, "constanciaRecepcion", "R-" + r.getFilename(), r.getConstanciaRecepcion());
        putBase64IfNotNull(json, "documentoFirmado", r.getFilename(), r.getDocumentoFirmado());
        if (r.getRepresentacionImpresa() != null && !r.getRepresentacionImpresa().isEmpty()) {
            putBase64IfNotNull(json, "representacionImpresa",
                    r.getFilename().replaceAll("\\.xml$", ".pdf"), r.getRepresentacionImpresa());
        }

        if (!r.isSuccess()) {
            json.put("errorCode", r.getErrorCode());
            json.put("errorMessage", r.getErrorMessage());
        }
        return json;
    }

    private static void putIfNotNull(Map<String, Object> json, String key, String value) {
        if (value != null) {
            json.put(key, value);
        }
    }

    private static void putBase64IfNotNull(Map<String, Object> json, String key, String filename, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            Map<String, Object> file = new HashMap<>();
            file.put("filename", filename);
            file.put("contentBase64", value);
            file.put("sizeBytes", decoded.length);
            json.put(key, file);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> file = new HashMap<>();
            file.put("filename", filename);
            file.put("contentBase64", value);
            json.put(key, file);
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> json = new HashMap<>();
        json.put("success", false);
        json.put("errorCode", code);
        json.put("errorMessage", message);
        return ResponseEntity.status(status).body(json);
    }
}