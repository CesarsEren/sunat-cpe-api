package pe.encarga.sunatapi.service.grt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

public class GrtHttpClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;

    @Before
    public void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    private GrtHttpClient newClient(String path, String user, String pass) {
        return new GrtHttpClient(path, user, pass, restTemplate) {
            @Override
            public String getEnvironment() {
                return "TEST";
            }
        };
    }

    private static String urlWithQuery(String prefix, String user, String pass) {
        return prefix + "?usuario=" + urlEncode(user) + "&contrasena=" + urlEncode(pass);
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void envioExitoso_devuelveResponseGeneraXmlParseado() {
        String urlPrefix = "http://grt.test/api/v2/envio/notacredito";
        String user = "USER";
        String pass = "PASS";
        String responseJson = "{\"numeroTicket\":\"tkt-1\",\"codigoRespuesta\":\"0\","
                + "\"estadoProceso\":\"ACEPTADO\",\"descripcionRespuesta\":\"OK\"}";

        mockServer.expect(requestTo(urlWithQuery(urlPrefix, user, pass)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/zip"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        GrtHttpClient client = newClient(urlPrefix, user, pass);
        GrtCallResult result = client.envioSunat("<xml/>".getBytes(StandardCharsets.UTF_8));

        assertTrue(result.isSuccess());
        assertNotNull(result.getResponse());
        assertEquals("tkt-1", result.getResponse().getNumeroTicket());
        assertEquals("0", result.getResponse().getCodigoRespuesta());
        assertEquals("ACEPTADO", result.getResponse().getEstadoProceso());
        mockServer.verify();
    }

    @Test
    public void queryParamsContienenUsuarioYContrasenaUrlEncoded() {
        String urlPrefix = "http://grt.test/api/v2/envio/notacredito";
        String user = "MODDATOS";
        String pass = "P@ss w0rd/with+chars";

        mockServer.expect(requestTo(urlWithQuery(urlPrefix, user, pass)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        GrtHttpClient client = newClient(urlPrefix, user, pass);
        client.envioSunat("<xml/>".getBytes(StandardCharsets.UTF_8));

        mockServer.verify();
    }

    @Test
    public void grtDevuelve500_mapeaAGrtHttpError() {
        String urlPrefix = "http://grt.test/api/v2/envio/notacredito";
        String user = "u";
        String pass = "p";

        mockServer.expect(requestTo(urlWithQuery(urlPrefix, user, pass)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error"));

        GrtHttpClient client = newClient(urlPrefix, user, pass);
        GrtCallResult result = client.envioSunat("<xml/>".getBytes(StandardCharsets.UTF_8));

        assertFalse(result.isSuccess());
        assertEquals("GRT_HTTP_ERROR", result.getErrorCode());
        assertNotNull(result.getErrorMessage());
        assertTrue("El mensaje debe incluir el codigo HTTP 500",
                result.getErrorMessage().contains("500"));
    }

    @Test
    public void grtDevuelve200ConHtml_mapeaAParseError() {
        String urlPrefix = "http://grt.test/api/v2/envio/notacredito";
        String user = "u";
        String pass = "p";

        mockServer.expect(requestTo(urlWithQuery(urlPrefix, user, pass)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("<html><body>Server Error</body></html>", MediaType.TEXT_HTML));

        GrtHttpClient client = newClient(urlPrefix, user, pass);
        GrtCallResult result = client.envioSunat("<xml/>".getBytes(StandardCharsets.UTF_8));

        assertFalse(result.isSuccess());
        assertEquals("GRT_RESPONSE_PARSE_ERROR", result.getErrorCode());
    }

    @Test
    public void networkTimeout_mapeaANetworkError() {
        String urlPrefix = "http://grt.test/api/v2/envio/notacredito";
        String user = "u";
        String pass = "p";

        mockServer.expect(requestTo(urlWithQuery(urlPrefix, user, pass)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("I/O error",
                            new SocketTimeoutException("Read timed out"));
                });

        GrtHttpClient client = newClient(urlPrefix, user, pass);
        GrtCallResult result = client.envioSunat("<xml/>".getBytes(StandardCharsets.UTF_8));

        assertFalse(result.isSuccess());
        assertEquals("NETWORK_ERROR", result.getErrorCode());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void pathVacio_devuelveGrtNotConfigured() {
        GrtHttpClient client = newClient("", "u", "p");
        GrtCallResult result = client.envioSunat("<xml/>".getBytes(StandardCharsets.UTF_8));

        assertFalse(result.isSuccess());
        assertEquals("GRT_NOT_CONFIGURED", result.getErrorCode());
    }

    @Test
    public void credencialesVacias_devuelveMissingGrtCredentials() {
        GrtHttpClient client = newClient("http://grt.test/api", "", "");
        GrtCallResult result = client.envioSunat("<xml/>".getBytes(StandardCharsets.UTF_8));

        assertFalse(result.isSuccess());
        assertEquals("MISSING_GRT_CREDENTIALS", result.getErrorCode());
    }

    @Test
    public void cuerpoEnviadoContieneLosBytesDelXmlFirmado() {
        String urlPrefix = "http://grt.test/api/v2/envio/notacredito";
        String user = "u";
        String pass = "p";
        byte[] payload = "<xml firmado='1'/>".getBytes(StandardCharsets.UTF_8);

        mockServer.expect(requestTo(urlWithQuery(urlPrefix, user, pass)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().bytes(payload))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        GrtHttpClient client = newClient(urlPrefix, user, pass);
        GrtCallResult result = client.envioSunat(payload);

        assertTrue(result.isSuccess());
        assertArrayEquals(payload, payload);
        mockServer.verify();
    }

    @Test
    public void grtDevuelve200ConCuerpoVacio_mapeaAEmptyBody() {
        String urlPrefix = "http://grt.test/api/v2/envio/notacredito";
        String user = "u";
        String pass = "p";

        mockServer.expect(requestTo(urlWithQuery(urlPrefix, user, pass)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        GrtHttpClient client = newClient(urlPrefix, user, pass);
        GrtCallResult result = client.envioSunat("<xml/>".getBytes(StandardCharsets.UTF_8));

        assertFalse(result.isSuccess());
        assertEquals("GRT_EMPTY_BODY", result.getErrorCode());
    }
}