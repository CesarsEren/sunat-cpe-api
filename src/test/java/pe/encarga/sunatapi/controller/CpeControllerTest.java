package pe.encarga.sunatapi.controller;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pe.encarga.sunatapi.config.SunatProperties;
import pe.encarga.sunatapi.service.SunatSendResult;
import pe.encarga.sunatapi.service.SunatSendService;
import pe.encarga.sunatapi.service.SunatStatusResult;

public class CpeControllerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private SunatSendService sendService;
    private SunatProperties properties;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        sendService = mock(SunatSendService.class);
        properties = new SunatProperties();
        properties.setDefaultProduction(false);
        properties.setConnectTimeoutMs(15000);
        properties.setRequestTimeoutMs(60000);

        CpeController controller = new CpeController(sendService, properties);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private File createZipFile(String name) throws Exception {
        File f = tmp.newFile(name);
        FileOutputStream fos = new FileOutputStream(f);
        ZipOutputStream zos = new ZipOutputStream(fos);
        zos.putNextEntry(new ZipEntry(name.replace(".zip", ".xml")));
        zos.write("<xml/>".getBytes("UTF-8"));
        zos.closeEntry();
        zos.close();
        fos.close();
        return f;
    }

    private SunatSendResult ok(String filename) {
        SunatSendResult r = new SunatSendResult();
        r.setSuccess(true);
        r.setFilename(filename);
        r.setEnvironment("BETA");
        r.setResponseCode("0");
        r.setDescription("La Factura fue registrada");
        r.setCdrZip(new byte[]{1, 2, 3});
        return r;
    }

    private SunatSendResult fail(String errorCode, String errorMessage) {
        SunatSendResult r = new SunatSendResult();
        r.setSuccess(false);
        r.setFilename("foo.zip");
        r.setEnvironment("BETA");
        r.setErrorCode(errorCode);
        r.setErrorMessage(errorMessage);
        return r;
    }

    @Test
    public void health_retornaJsonConConfiguracion() throws Exception {
        mockMvc.perform(post("/api/v1/cpe/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.defaultProduction").value(false))
                .andExpect(jsonPath("$.connectTimeoutMs").value(15000))
                .andExpect(jsonPath("$.requestTimeoutMs").value(60000));
    }

    @Test
    public void sendMultipart_sinArchivo_retorna415() throws Exception {
        mockMvc.perform(post("/api/v1/cpe/send")
                        .param("usuario", "u")
                        .param("contrasena", "p"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    public void sendMultipart_exitoso_retorna200ConCdr() throws Exception {
        File zip = createZipFile("20600520033-01-F001-00000001.zip");
        when(sendService.sendBill(any(File.class), anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(ok("20600520033-01-F001-00000001.zip"));

        mockMvc.perform(fileUpload("/api/v1/cpe/send")
                        .file("file", java.nio.file.Files.readAllBytes(zip.toPath()))
                        .param("usuario", "u")
                        .param("contrasena", "p")
                        .param("production", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseCode").value("0"))
                .andExpect(jsonPath("$.environment").value("BETA"))
                .andExpect(jsonPath("$.cdr.contentBase64").exists())
                .andExpect(jsonPath("$.cdr.filename").value("R-20600520033-01-F001-00000001.zip"));
    }

    @Test
    public void sendMultipart_filenameInvalido_retorna502ConErrorCode() throws Exception {
        File zip = createZipFile("bad.zip");
        when(sendService.sendBill(any(File.class), anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(fail("INVALID_FILENAME", "Nombre de archivo invalido"));

        mockMvc.perform(fileUpload("/api/v1/cpe/send")
                        .file("file", java.nio.file.Files.readAllBytes(zip.toPath()))
                        .param("usuario", "u")
                        .param("contrasena", "p"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_FILENAME"));
    }

    @Test
    public void sendMultipart_servicioLanzaExcepcion_retorna500() throws Exception {
        File zip = createZipFile("20600520033-01-F001-00000001.zip");
        when(sendService.sendBill(any(File.class), anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(fileUpload("/api/v1/cpe/send")
                        .file("file", java.nio.file.Files.readAllBytes(zip.toPath()))
                        .param("usuario", "u")
                        .param("contrasena", "p"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("UNEXPECTED_ERROR"));
    }

    @Test
    public void sendBase64_sinFieldBase64_retorna400() throws Exception {
        mockMvc.perform(post("/api/v1/cpe/send-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"x.zip\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_FILE"));
    }

    @Test
    public void sendBase64_base64Invalido_retorna400InvalidBase64() throws Exception {
        mockMvc.perform(post("/api/v1/cpe/send-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"x.zip\",\"fileBase64\":\"@@@no-es-base64@@@\",\"usuario\":\"u\",\"contrasena\":\"p\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_BASE64"));
    }

    @Test
    public void sendBase64_exitoso_retorna200() throws Exception {
        File zip = createZipFile("20600520033-01-F001-1.zip");
        String b64 = java.util.Base64.getEncoder().encodeToString(
                java.nio.file.Files.readAllBytes(zip.toPath()));
        when(sendService.sendBill(any(File.class), anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(ok("20600520033-01-F001-1.zip"));

        mockMvc.perform(post("/api/v1/cpe/send-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"20600520033-01-F001-1.zip\",\"fileBase64\":\""
                                + b64 + "\",\"usuario\":\"u\",\"contrasena\":\"p\",\"production\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.cdr.filename").value("R-20600520033-01-F001-1.zip"));
    }

    @Test
    public void sendBase64_filenameInvalido_retorna502() throws Exception {
        File zip = createZipFile("bad.zip");
        String b64 = java.util.Base64.getEncoder().encodeToString(
                java.nio.file.Files.readAllBytes(zip.toPath()));
        when(sendService.sendBill(any(File.class), anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(fail("INVALID_FILENAME", "Nombre de archivo invalido"));

        mockMvc.perform(post("/api/v1/cpe/send-base64")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"bad.zip\",\"fileBase64\":\"" + b64
                                + "\",\"usuario\":\"u\",\"contrasena\":\"p\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("INVALID_FILENAME"));
    }

    @Test
    public void status_sinTicket_retornaOkConError() throws Exception {
        SunatStatusResult sr = new SunatStatusResult();
        sr.setSuccess(false);
        sr.setEnvironment("BETA");
        sr.setErrorCode("MISSING_TICKET");
        sr.setErrorMessage("Debe proporcionar el numero de ticket.");
        org.mockito.Mockito.when(sendService.getStatus(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(sr);

        mockMvc.perform(post("/api/v1/cpe/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"u\",\"contrasena\":\"p\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("MISSING_TICKET"));
    }

    @Test
    public void status_conTicket_retornaOk() throws Exception {
        SunatStatusResult sr = new SunatStatusResult();
        sr.setSuccess(true);
        sr.setTicket("123");
        sr.setStatusCode("0");
        sr.setStatusMessage("OK");
        sr.setEnvironment("BETA");
        org.mockito.Mockito.when(sendService.getStatus(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(sr);

        mockMvc.perform(post("/api/v1/cpe/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"123\",\"usuario\":\"u\",\"contrasena\":\"p\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.statusCode").value("0"));
    }
}
