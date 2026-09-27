package pe.encarga.sunatapi.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.soap.SOAPFault;
import javax.xml.ws.soap.SOAPFaultException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

public class SunatSendServiceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private SunatSoapClient prdClient;
    private SunatSoapClient betaClient;
    private CdrParser cdrParser;
    private SunatSendService service;

    @Before
    public void setUp() {
        prdClient = mock(SunatSoapClient.class);
        betaClient = mock(SunatSoapClient.class);
        cdrParser = mock(CdrParser.class);

        when(prdClient.getEnvironment()).thenReturn("PRD");
        when(betaClient.getEnvironment()).thenReturn("BETA");

        service = new SunatSendService(prdClient, betaClient, cdrParser);
    }

    private static SOAPFaultException newSoapFault(String message) {
        SOAPFault fault = mock(SOAPFault.class);
        when(fault.getFaultString()).thenReturn(message);
        return new SOAPFaultException(fault);
    }

    private File createZipWithName(String name) throws IOException {
        File zip = tmp.newFile(name);
        FileOutputStream fos = new FileOutputStream(zip);
        ZipOutputStream zos = new ZipOutputStream(fos);
        zos.putNextEntry(new ZipEntry(name.replace(".zip", ".xml")));
        zos.write("<xml/>".getBytes("UTF-8"));
        zos.closeEntry();
        zos.close();
        fos.close();
        return zip;
    }

    @Test
    public void sendBill_archivoNoExiste_devuelveFileNotFound() {
        File missing = new File(tmp.getRoot(), "no-existe.zip");

        SunatSendResult r = service.sendBill(missing, "20600520033-01-F001-1.zip", "user", "pass", false, "");

        assertFalse(r.isSuccess());
        assertEquals("FILE_NOT_FOUND", r.getErrorCode());
        assertTrue(r.getErrorMessage().contains("no existe"));
        verify(betaClient, never()).sendBill(anyString(), any(File.class), anyString(), anyString(), anyString());
    }

    @Test
    public void sendBill_filenameInvalido_devuelveInvalidFilename() throws IOException {
        File zip = createZipWithName("foo-bar.zip");

        SunatSendResult r = service.sendBill(zip, "foo-bar.zip", "user", "pass", false, "");

        assertFalse(r.isSuccess());
        assertEquals("INVALID_FILENAME", r.getErrorCode());
        assertTrue(r.getErrorMessage().contains("Recibido: foo-bar.zip"));
        verify(betaClient, never()).sendBill(anyString(), any(File.class), anyString(), anyString(), anyString());
    }

    @Test
    public void sendBill_filenameLowerCaseTipoRechazado() throws IOException {
        File zip = createZipWithName("20600520033-f001-00000001.zip");

        SunatSendResult r = service.sendBill(zip, "20600520033-f001-00000001.zip", "user", "pass", false, "");

        assertFalse(r.isSuccess());
        assertEquals("INVALID_FILENAME", r.getErrorCode());
    }

    @Test
    public void sendBill_usernameVacio_devuelveMissingCredentials() throws IOException {
        File zip = createZipWithName("20600520033-01-F001-00000001.zip");

        SunatSendResult r = service.sendBill(zip, "20600520033-01-F001-00000001.zip", "", "pass", false, "");

        assertFalse(r.isSuccess());
        assertEquals("MISSING_CREDENTIALS", r.getErrorCode());
    }

    @Test
    public void sendBill_passwordVacio_devuelveMissingCredentials() throws IOException {
        File zip = createZipWithName("20600520033-01-F001-00000001.zip");

        SunatSendResult r = service.sendBill(zip, "20600520033-01-F001-00000001.zip", "user", "   ", false, "");

        assertFalse(r.isSuccess());
        assertEquals("MISSING_CREDENTIALS", r.getErrorCode());
    }

    @Test
    public void sendBill_credencialesNull_devuelveMissingCredentials() throws IOException {
        File zip = createZipWithName("20600520033-01-F001-00000001.zip");

        SunatSendResult r = service.sendBill(zip, "20600520033-01-F001-00000001.zip", null, null, false, "");

        assertFalse(r.isSuccess());
        assertEquals("MISSING_CREDENTIALS", r.getErrorCode());
    }

    @Test
    public void sendBill_productionFalseUsaBetaClient_exitoso() throws IOException {
        File zip = createZipWithName("20600520033-01-F001-00000001.zip");
        byte[] cdr = { 1, 2, 3 };

        when(betaClient.sendBill(eq("20600520033-01-F001-00000001.zip"), eq(zip),
                eq("u"), eq("p"), eq("")))
                .thenReturn(cdr);
        when(cdrParser.parse(cdr)).thenReturn(CdrParseResult.ok("0", "La Factura F001-1 fue registrada"));

        SunatSendResult r = service.sendBill(zip, "20600520033-01-F001-00000001.zip",
                "u", "p", false, "");

        assertTrue(r.isSuccess());
        assertEquals("0", r.getResponseCode());
        assertEquals("La Factura F001-1 fue registrada", r.getDescription());
        assertEquals("BETA", r.getEnvironment());
        assertArrayEquals(cdr, r.getCdrZip());
        verify(prdClient, never()).sendBill(anyString(), any(File.class), anyString(), anyString(), anyString());
    }

    @Test
    public void sendBill_productionTrueUsaPrdClient_exitoso() throws IOException {
        File zip = createZipWithName("20600520033-03-B001-00000099.zip");
        byte[] cdr = { 9, 9, 9 };

        when(prdClient.sendBill(eq("20600520033-03-B001-00000099.zip"), eq(zip),
                eq("u"), eq("p"), eq("")))
                .thenReturn(cdr);
        when(cdrParser.parse(cdr)).thenReturn(CdrParseResult.ok("0", "La Boleta B001-99 fue registrada"));

        SunatSendResult r = service.sendBill(zip, "20600520033-03-B001-00000099.zip",
                "u", "p", true, "");

        assertTrue(r.isSuccess());
        assertEquals("PRD", r.getEnvironment());
        verify(betaClient, never()).sendBill(anyString(), any(File.class), anyString(), anyString(), anyString());
    }

    @Test
    public void sendBill_respuestaRechazada_devuelveSunatRejected() throws IOException {
        File zip = createZipWithName("20600520033-01-F001-00000001.zip");
        byte[] cdr = { 0 };

        when(betaClient.sendBill(anyString(), any(File.class), anyString(), anyString(), anyString()))
                .thenReturn(cdr);
        when(cdrParser.parse(cdr)).thenReturn(
                CdrParseResult.rejected("2300", "El comprobante ya fue registrado anteriormente"));

        SunatSendResult r = service.sendBill(zip, "20600520033-01-F001-00000001.zip",
                "u", "p", false, "");

        assertFalse(r.isSuccess());
        assertEquals("2300", r.getResponseCode());
        assertEquals("El comprobante ya fue registrado anteriormente", r.getDescription());
        assertEquals("SUNAT_REJECTED", r.getErrorCode());
        assertEquals("El comprobante ya fue registrado anteriormente", r.getErrorMessage());
        assertArrayEquals(cdr, r.getCdrZip());
    }

    @Test
    public void sendBill_cdrParseError_devuelveCdrParseError() throws IOException {
        File zip = createZipWithName("20600520033-01-F001-00000001.zip");
        byte[] cdr = { 1, 1 };

        when(betaClient.sendBill(anyString(), any(File.class), anyString(), anyString(), anyString()))
                .thenReturn(cdr);
        when(cdrParser.parse(cdr)).thenReturn(
                CdrParseResult.error("CDR_PARSE_ERROR", "ZIP corrupto"));

        SunatSendResult r = service.sendBill(zip, "20600520033-01-F001-00000001.zip",
                "u", "p", false, "");

        assertFalse(r.isSuccess());
        assertEquals("CDR_PARSE_ERROR", r.getErrorCode());
        assertEquals("ZIP corrupto", r.getErrorMessage());
        assertNull(r.getResponseCode());
    }

    @Test
    public void sendBill_soapFaultException_devuelveSoapFault() throws IOException {
        File zip = createZipWithName("20600520033-01-F001-00000001.zip");
        SOAPFaultException ex = newSoapFault("Credenciales invalidas");

        doThrow(ex).when(betaClient).sendBill(anyString(), any(File.class), anyString(), anyString(), anyString());

        SunatSendResult r = service.sendBill(zip, "20600520033-01-F001-00000001.zip",
                "u", "p", false, "");

        assertFalse(r.isSuccess());
        assertEquals("SOAP_FAULT", r.getErrorCode());
        assertTrue("Mensaje debe contener credenciales",
                r.getErrorMessage().toLowerCase().contains("credenciales"));
    }

    @Test
    public void sendBill_excepcionGenerica_devuelveClassName() throws IOException {
        File zip = createZipWithName("20600520033-01-F001-00000001.zip");

        when(betaClient.sendBill(anyString(), any(File.class), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Fallo de red"));

        SunatSendResult r = service.sendBill(zip, "20600520033-01-F001-00000001.zip",
                "u", "p", false, "");

        assertFalse(r.isSuccess());
        assertEquals("RuntimeException", r.getErrorCode());
        assertEquals("Fallo de red", r.getErrorMessage());
    }

    @Test
    public void sendBill_excepcionSinMensaje_devuelveClassNameComoMensaje() throws IOException {
        File zip = createZipWithName("20600520033-01-F001-00000001.zip");

        when(betaClient.sendBill(anyString(), any(File.class), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException((String) null));

        SunatSendResult r = service.sendBill(zip, "20600520033-01-F001-00000001.zip",
                "u", "p", false, "");

        assertFalse(r.isSuccess());
        assertEquals("RuntimeException", r.getErrorCode());
        assertEquals("RuntimeException", r.getErrorMessage());
    }

    @Test
    public void sendBill_partyTypeNull_prdSeMandaVacio() throws IOException {
        File zip = createZipWithName("20600520033-01-F001-00000001.zip");

        when(prdClient.sendBill(anyString(), any(File.class), anyString(), anyString(), eq("")))
                .thenReturn(new byte[]{0});
        when(cdrParser.parse(any(byte[].class))).thenReturn(CdrParseResult.ok("0", "ok"));

        service.sendBill(zip, "20600520033-01-F001-00000001.zip", "u", "p", true, null);

        ArgumentCaptor<String> pt = ArgumentCaptor.forClass(String.class);
        verify(prdClient).sendBill(anyString(), any(File.class), anyString(), anyString(), pt.capture());
        assertEquals("", pt.getValue());
    }

    @Test
    public void sendBill_filenameSeTomaDelOriginalNoDelFile() throws IOException {
        File zip = createZipWithName("cpe-987654321-20600520033-01-F001-00000001.zip");

        when(betaClient.sendBill(eq("20600520033-01-F001-00000001.zip"), any(File.class),
                anyString(), anyString(), anyString()))
                .thenReturn(new byte[]{0});
        when(cdrParser.parse(any(byte[].class))).thenReturn(CdrParseResult.ok("0", "ok"));

        SunatSendResult r = service.sendBill(zip, "20600520033-01-F001-00000001.zip",
                "u", "p", false, "");

        assertTrue(r.isSuccess());
        assertEquals("20600520033-01-F001-00000001.zip", r.getFilename());
    }

    @Test
    public void sendBill_filenameOriginalNullUsaNombreDelArchivo() throws IOException {
        File zip = createZipWithName("20600520033-01-F001-00000001.zip");

        when(betaClient.sendBill(eq("20600520033-01-F001-00000001.zip"), any(File.class),
                anyString(), anyString(), anyString()))
                .thenReturn(new byte[]{0});
        when(cdrParser.parse(any(byte[].class))).thenReturn(CdrParseResult.ok("0", "ok"));

        SunatSendResult r = service.sendBill(zip, null, "u", "p", false, "");

        assertTrue(r.isSuccess());
        assertEquals("20600520033-01-F001-00000001.zip", r.getFilename());
    }

    @Test
    public void getStatus_ticketVacio_devuelveMissingTicket() {
        SunatStatusResult r = service.getStatus("   ", "u", "p", false);

        assertFalse(r.isSuccess());
        assertEquals("MISSING_TICKET", r.getErrorCode());
        verify(betaClient, never()).getStatus(anyString(), anyString(), anyString());
    }

    @Test
    public void getStatus_credencialesVacias_devuelveMissingCredentials() {
        SunatStatusResult r = service.getStatus("20170012345678901", "", "p", false);

        assertFalse(r.isSuccess());
        assertEquals("MISSING_CREDENTIALS", r.getErrorCode());
        verify(betaClient, never()).getStatus(anyString(), anyString(), anyString());
    }

    @Test
    public void getStatus_exitoso_devuelveStatusCode() {
        SunatStatusResult mockResult = new SunatStatusResult();
        mockResult.setTicket("20170012345678901");
        mockResult.setEnvironment("BETA");
        mockResult.setStatusCode("0");
        mockResult.setStatusMessage("Resumen registrado");
        mockResult.setSuccess(true);
        when(betaClient.getStatus("20170012345678901", "u", "p")).thenReturn(mockResult);

        SunatStatusResult r = service.getStatus("20170012345678901", "u", "p", false);

        assertNotNull(r);
        assertTrue(r.isSuccess());
        assertEquals("0", r.getStatusCode());
        assertEquals("20170012345678901", r.getTicket());
        assertEquals("BETA", r.getEnvironment());
    }

    @Test
    public void getStatus_productionTrueUsaPrdClient() {
        SunatStatusResult mockResult = new SunatStatusResult();
        mockResult.setStatusCode("0");
        mockResult.setSuccess(true);
        when(prdClient.getStatus("tkt", "u", "p")).thenReturn(mockResult);

        service.getStatus("tkt", "u", "p", true);

        verify(prdClient).getStatus("tkt", "u", "p");
        verify(betaClient, never()).getStatus(anyString(), anyString(), anyString());
    }

    @Test
    public void getStatus_soapFault_devuelveSoapFault() {
        doThrow(newSoapFault("error SOAP"))
                .when(betaClient).getStatus("tkt", "u", "p");

        SunatStatusResult r = service.getStatus("tkt", "u", "p", false);

        assertFalse(r.isSuccess());
        assertEquals("SOAP_FAULT", r.getErrorCode());
    }

    @Test
    public void getStatus_excepcionGenerica_devuelveClassName() {
        when(betaClient.getStatus(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("timeout"));

        SunatStatusResult r = service.getStatus("tkt", "u", "p", false);

        assertFalse(r.isSuccess());
        assertEquals("RuntimeException", r.getErrorCode());
        assertEquals("timeout", r.getErrorMessage());
    }
}