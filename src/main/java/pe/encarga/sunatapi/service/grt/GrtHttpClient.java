package pe.encarga.sunatapi.service.grt;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public abstract class GrtHttpClient implements GrtClient {

    private static final Log log = LogFactory.getLog(GrtHttpClient.class);

    private static final int MAX_ERROR_LENGTH = 500;

    private final String pathEnviar;
    private final String usuario;
    private final String contrasena;
    private final RestTemplate restTemplate;

    protected GrtHttpClient(String pathEnviar, String usuario, String contrasena, RestTemplate restTemplate) {
        this.pathEnviar = pathEnviar;
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.restTemplate = restTemplate;
    }

    @Override
    public GrtCallResult envioSunat(byte[] xmlFirmado) {
        long start = System.currentTimeMillis();

        if (pathEnviar == null || pathEnviar.trim().isEmpty()) {
            return GrtCallResult.error("GRT_NOT_CONFIGURED",
                    "La URL del servicio GRT no esta configurada (pathEnviar vacia).");
        }
        if (usuario == null || usuario.trim().isEmpty()
                || contrasena == null || contrasena.trim().isEmpty()) {
            return GrtCallResult.error("MISSING_GRT_CREDENTIALS",
                    "Las credenciales GRT no estan configuradas (app.api.grt.user / app.api.grt.pass).");
        }

        URI uri;
        try {
            uri = new URI(pathEnviar + "?usuario=" + urlEncode(usuario)
                    + "&contrasena=" + urlEncode(contrasena));
        } catch (Exception ex) {
            return GrtCallResult.error("GRT_URL_INVALID",
                    "URL GRT invalida: " + ex.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/zip"));

        HttpEntity<byte[]> request = new HttpEntity<>(xmlFirmado, headers);

        try {
            ResponseEntity<ResponseGeneraXml> response = restTemplate.exchange(
                    uri, HttpMethod.POST, request, ResponseGeneraXml.class);
            ResponseGeneraXml body = response.getBody();
            if (body == null) {
                return GrtCallResult.error("GRT_EMPTY_BODY",
                        "GRT devolvio 200 pero el cuerpo de la respuesta esta vacio.");
            }
            log.info(String.format("Envio GRT OK [%s] duracion=%dms ticket=%s codigoRespuesta=%s",
                    getEnvironment(), System.currentTimeMillis() - start,
                    body.getNumeroTicket(), body.getCodigoRespuesta()));
            return GrtCallResult.ok(body);

        } catch (HttpStatusCodeException hsce) {
            String body = hsce.getResponseBodyAsString();
            String msg = truncate("GRT devolvio HTTP " + hsce.getStatusCode()
                    + (body == null || body.isEmpty() ? "" : ": " + body), MAX_ERROR_LENGTH);
            log.error(String.format("Envio GRT FAIL [%s] duracion=%dms http=%d mensaje=%s",
                    getEnvironment(), System.currentTimeMillis() - start,
                    hsce.getStatusCode().value(), msg), hsce);
            return GrtCallResult.error("GRT_HTTP_ERROR", msg);

        } catch (ResourceAccessException rae) {
            String msg = truncate("No se pudo conectar con GRT: " + rae.getMessage(), MAX_ERROR_LENGTH);
            log.error(String.format("Envio GRT FAIL [%s] duracion=%dms network_error=%s",
                    getEnvironment(), System.currentTimeMillis() - start, msg), rae);
            return GrtCallResult.error("NETWORK_ERROR", msg);

        } catch (HttpMessageNotReadableException hmne) {
            String msg = truncate("GRT devolvio 200 pero el body no es JSON valido: "
                    + hmne.getMessage(), MAX_ERROR_LENGTH);
            log.error(String.format("Envio GRT FAIL [%s] duracion=%dms parse_error=%s",
                    getEnvironment(), System.currentTimeMillis() - start, msg), hmne);
            return GrtCallResult.error("GRT_RESPONSE_PARSE_ERROR", msg);

        } catch (RestClientException rce) {
            String msg = truncate("Error al parsear respuesta GRT: " + rce.getMessage(), MAX_ERROR_LENGTH);
            log.error(String.format("Envio GRT FAIL [%s] duracion=%dms rest_error=%s",
                    getEnvironment(), System.currentTimeMillis() - start, msg), rce);
            return GrtCallResult.error("GRT_RESPONSE_PARSE_ERROR", msg);

        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName()
                    : truncate(ex.getMessage(), MAX_ERROR_LENGTH);
            log.error(String.format("Envio GRT FAIL [%s] duracion=%dms error=%s",
                    getEnvironment(), System.currentTimeMillis() - start, msg), ex);
            return GrtCallResult.error(ex.getClass().getSimpleName(), msg);
        }
    }

    private static String urlEncode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}