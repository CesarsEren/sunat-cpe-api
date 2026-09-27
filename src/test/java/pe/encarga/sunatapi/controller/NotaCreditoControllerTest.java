package pe.encarga.sunatapi.controller;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pe.encarga.sunatapi.service.NotaCreditoSendResult;
import pe.encarga.sunatapi.service.NotaCreditoSendService;

public class NotaCreditoControllerTest {

    private NotaCreditoSendService sendService;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        sendService = mock(NotaCreditoSendService.class);
        NotaCreditoController controller = new NotaCreditoController(sendService, false);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private byte[] xmlValido() {
        return "<?xml version=\"1.0\"?><CreditNote/>".getBytes();
    }

    private NotaCreditoSendResult ok(String filename) {
        NotaCreditoSendResult r = new NotaCreditoSendResult();
        r.setSuccess(true);
        r.setFilename(filename);
        r.setEnvironment("BETA");
        r.setNumeroTicket("tkt-1");
        r.setCodigoRespuesta("0");
        r.setDescripcionRespuesta("La Nota de Credito F001-1 fue aceptada");
        r.setEstadoProceso("ACEPTADO");
        r.setValorResumen("a1b2c3d4");
        r.setCodigoBarras("|barras|");
        r.setCodigoMensaje("0");
        r.setObservaciones(Arrays.asList());
        r.setConstanciaRecepcion("UEsDBAoAAAAA");
        r.setDocumentoFirmado("PD94bWw=");
        return r;
    }

    private NotaCreditoSendResult fail(String code, String msg) {
        NotaCreditoSendResult r = new NotaCreditoSendResult();
        r.setSuccess(false);
        r.setFilename("x.xml");
        r.setEnvironment("BETA");
        r.setErrorCode(code);
        r.setErrorMessage(msg);
        return r;
    }

    @Test
    public void sendMultipart_exitoso_retorna200ConCamposDeResponseGeneraXml() throws Exception {
        byte[] xml = xmlValido();
        when(sendService.send(any(), any(), anyBoolean())).thenReturn(ok("20600520033-07-F001-1.xml"));

        mockMvc.perform(fileUpload("/api/v1/notacredito/send")
                        .file("file", xml)
                        .param("production", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.environment").value("BETA"))
                .andExpect(jsonPath("$.numeroTicket").value("tkt-1"))
                .andExpect(jsonPath("$.codigoRespuesta").value("0"))
                .andExpect(jsonPath("$.estadoProceso").value("ACEPTADO"))
                .andExpect(jsonPath("$.valorResumen").value("a1b2c3d4"))
                .andExpect(jsonPath("$.codigoBarras").value("|barras|"))
                .andExpect(jsonPath("$.constanciaRecepcion.contentBase64").value("UEsDBAoAAAAA"))
                .andExpect(jsonPath("$.constanciaRecepcion.filename").value("R-20600520033-07-F001-1.xml"))
                .andExpect(jsonPath("$.documentoFirmado.contentBase64").value("PD94bWw="));
    }

    @Test
    public void sendMultipart_productionNull_caeEnDefaultFalse() throws Exception {
        byte[] xml = xmlValido();
        when(sendService.send(any(), any(), anyBoolean())).thenReturn(ok("x.xml"));

        mockMvc.perform(fileUpload("/api/v1/notacredito/send")
                        .file("file", xml))
                .andExpect(status().isOk());
    }

    @Test
    public void sendMultipart_sinArchivo_retornaUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/notacredito/send"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    public void sendMultipart_grtRechaza_retorna502ConSunatRejected() throws Exception {
        byte[] xml = xmlValido();
        when(sendService.send(any(), any(), anyBoolean()))
                .thenReturn(fail("SUNAT_REJECTED", "El comprobante ya fue registrado"));

        mockMvc.perform(fileUpload("/api/v1/notacredito/send")
                        .file("file", xml))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SUNAT_REJECTED"))
                .andExpect(jsonPath("$.errorMessage").value("El comprobante ya fue registrado"));
    }

    @Test
    public void sendMultipart_xmlInvalido_retorna400() throws Exception {
        byte[] bad = "esto no es xml".getBytes();
        when(sendService.send(any(), any(), anyBoolean()))
                .thenReturn(fail("INVALID_XML", "El archivo enviado no es un XML valido"));

        mockMvc.perform(fileUpload("/api/v1/notacredito/send")
                        .file("file", bad))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("INVALID_XML"));
    }

    @Test
    public void sendMultipart_servicioLanzaExcepcion_retorna500() throws Exception {
        byte[] xml = xmlValido();
        when(sendService.send(any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(fileUpload("/api/v1/notacredito/send")
                        .file("file", xml))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("UNEXPECTED_ERROR"));
    }

    @Test
    public void sendBase64_sinFileBase64_retorna400() throws Exception {
        mockMvc.perform(post("/api/v1/notacredito/send-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"x.xml\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_FILE"));
    }

    @Test
    public void sendBase64_base64Invalido_retorna400() throws Exception {
        mockMvc.perform(post("/api/v1/notacredito/send-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"x.xml\",\"fileBase64\":\"@@@no-es-base64@@@\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_BASE64"));
    }

    @Test
    public void sendBase64_exitoso_retorna200ConResponseGeneraXml() throws Exception {
        String xmlB64 = java.util.Base64.getEncoder().encodeToString(xmlValido());
        when(sendService.send(any(), any(), anyBoolean())).thenReturn(ok("20600520033-07-F001-1.xml"));

        mockMvc.perform(post("/api/v1/notacredito/send-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"20600520033-07-F001-1.xml\","
                                + "\"fileBase64\":\"" + xmlB64 + "\","
                                + "\"production\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.numeroTicket").value("tkt-1"));
    }

    @Test
    public void sendBase64_productionNull_caeEnDefaultProperties() throws Exception {
        String xmlB64 = java.util.Base64.getEncoder().encodeToString(xmlValido());
        when(sendService.send(any(), any(), anyBoolean())).thenReturn(ok("x.xml"));

        mockMvc.perform(post("/api/v1/notacredito/send-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"x.xml\",\"fileBase64\":\"" + xmlB64 + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    public void sendBase64_grtHttpError_retorna502() throws Exception {
        String xmlB64 = java.util.Base64.getEncoder().encodeToString(xmlValido());
        when(sendService.send(any(), any(), anyBoolean()))
                .thenReturn(fail("GRT_HTTP_ERROR", "GRT devolvio HTTP 500"));

        mockMvc.perform(post("/api/v1/notacredito/send-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"x.xml\",\"fileBase64\":\"" + xmlB64 + "\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("GRT_HTTP_ERROR"));
    }

    @Test
    public void sendBase64_servicioLanzaExcepcion_retorna500() throws Exception {
        String xmlB64 = java.util.Base64.getEncoder().encodeToString(xmlValido());
        when(sendService.send(any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("exploto"));

        mockMvc.perform(post("/api/v1/notacredito/send-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"x.xml\",\"fileBase64\":\"" + xmlB64 + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("UNEXPECTED_ERROR"));
    }

    @Test
    public void constructor_conDefaultProductionTrue_aceptaProductionFalseEnRequest() throws Exception {
        NotaCreditoSendService svc = mock(NotaCreditoSendService.class);
        NotaCreditoController controller = new NotaCreditoController(svc, true);
        MockMvc mm = MockMvcBuilders.standaloneSetup(controller).build();
        byte[] xml = xmlValido();
        when(svc.send(any(), any(), anyBoolean())).thenReturn(ok("x.xml"));

        mm.perform(fileUpload("/api/v1/notacredito/send")
                        .file("file", xml)
                        .param("production", "false"))
                .andExpect(status().isOk());
    }
}