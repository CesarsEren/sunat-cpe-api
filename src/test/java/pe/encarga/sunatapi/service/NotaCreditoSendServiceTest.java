package pe.encarga.sunatapi.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import pe.encarga.sunatapi.service.grt.GrtCallResult;
import pe.encarga.sunatapi.service.grt.GrtClient;
import pe.encarga.sunatapi.service.grt.ResponseGeneraXml;

public class NotaCreditoSendServiceTest {

    private GrtClient prdClient;
    private GrtClient betaClient;
    private NotaCreditoSendService service;

    @Before
    public void setUp() {
        prdClient = mock(GrtClient.class);
        betaClient = mock(GrtClient.class);
        when(prdClient.getEnvironment()).thenReturn("PRD");
        when(betaClient.getEnvironment()).thenReturn("BETA");
        service = new NotaCreditoSendService(prdClient, betaClient);
    }

    private byte[] xmlValido() {
        return "<?xml version=\"1.0\"?><CreditNote/>".getBytes();
    }

    private ResponseGeneraXml responseOk() {
        ResponseGeneraXml r = new ResponseGeneraXml();
        r.setNumeroTicket("tkt-1");
        r.setCodigoRespuesta("0");
        r.setDescripcionRespuesta("La Nota de Credito F001-1 fue aceptada");
        r.setEstadoProceso("ACEPTADO");
        r.setObservaciones(Arrays.asList());
        return r;
    }

    @Test
    public void xmlNull_devuelveInvalidXml() {
        NotaCreditoSendResult r = service.send(null, "x.xml", false);

        assertFalse(r.isSuccess());
        assertEquals("INVALID_XML", r.getErrorCode());
        verify(betaClient, never()).envioSunat(any());
    }

    @Test
    public void xmlVacio_devuelveInvalidXml() {
        NotaCreditoSendResult r = service.send(new byte[0], "x.xml", false);

        assertFalse(r.isSuccess());
        assertEquals("INVALID_XML", r.getErrorCode());
    }

    @Test
    public void xmlNoParseable_devuelveInvalidXml() {
        NotaCreditoSendResult r = service.send("esto no es xml <<<".getBytes(), "x.xml", false);

        assertFalse(r.isSuccess());
        assertEquals("INVALID_XML", r.getErrorCode());
    }

    @Test
    public void xmlValido_productionFalse_usaBetaClient_exitoso() {
        when(betaClient.envioSunat(any())).thenReturn(GrtCallResult.ok(responseOk()));

        NotaCreditoSendResult r = service.send(xmlValido(), "20600520033-07-F001-1.xml", false);

        assertTrue(r.isSuccess());
        assertEquals("BETA", r.getEnvironment());
        assertEquals("tkt-1", r.getNumeroTicket());
        assertEquals("0", r.getCodigoRespuesta());
        assertEquals("ACEPTADO", r.getEstadoProceso());
        verify(betaClient).envioSunat(any());
        verify(prdClient, never()).envioSunat(any());
    }

    @Test
    public void xmlValido_productionTrue_usaPrdClient_exitoso() {
        when(prdClient.envioSunat(any())).thenReturn(GrtCallResult.ok(responseOk()));

        NotaCreditoSendResult r = service.send(xmlValido(), "20600520033-07-F001-1.xml", true);

        assertTrue(r.isSuccess());
        assertEquals("PRD", r.getEnvironment());
        verify(prdClient).envioSunat(any());
        verify(betaClient, never()).envioSunat(any());
    }

    @Test
    public void grtDevuelveCodigoExcepcion_mapeaASunatRejected() {
        ResponseGeneraXml r = new ResponseGeneraXml();
        r.setCodigoRespuesta("2300");
        r.setCodigoExcepcion("001");
        r.setDescripcionExcepcion("El comprobante ya fue registrado");
        r.setDescripcionRespuesta("rechazado");
        when(betaClient.envioSunat(any())).thenReturn(GrtCallResult.ok(r));

        NotaCreditoSendResult result = service.send(xmlValido(), "x.xml", false);

        assertFalse(result.isSuccess());
        assertEquals("SUNAT_REJECTED", result.getErrorCode());
        assertEquals("El comprobante ya fue registrado", result.getErrorMessage());
        assertEquals("2300", result.getCodigoRespuesta());
        assertEquals("001", result.getCodigoExcepcion());
    }

    @Test
    public void grtDevuelveCodigoRespuestaNoCero_mapeaASunatRejected() {
        ResponseGeneraXml r = new ResponseGeneraXml();
        r.setCodigoRespuesta("9999");
        r.setDescripcionRespuesta("rechazo generico");
        when(betaClient.envioSunat(any())).thenReturn(GrtCallResult.ok(r));

        NotaCreditoSendResult result = service.send(xmlValido(), "x.xml", false);

        assertFalse(result.isSuccess());
        assertEquals("SUNAT_REJECTED", result.getErrorCode());
        assertEquals("rechazo generico", result.getErrorMessage());
    }

    @Test
    public void grtDevuelveHttpError_sePropagaErrorCode() {
        when(betaClient.envioSunat(any())).thenReturn(
                GrtCallResult.error("GRT_HTTP_ERROR", "GRT devolvio HTTP 500"));

        NotaCreditoSendResult r = service.send(xmlValido(), "x.xml", false);

        assertFalse(r.isSuccess());
        assertEquals("GRT_HTTP_ERROR", r.getErrorCode());
        assertEquals("GRT devolvio HTTP 500", r.getErrorMessage());
    }

    @Test
    public void grtDevuelveNetworkError_sePropagaErrorCode() {
        when(betaClient.envioSunat(any())).thenReturn(
                GrtCallResult.error("NETWORK_ERROR", "timeout"));

        NotaCreditoSendResult r = service.send(xmlValido(), "x.xml", false);

        assertFalse(r.isSuccess());
        assertEquals("NETWORK_ERROR", r.getErrorCode());
    }

    @Test
    public void xmlValido_camposCompletosDelResponse_seMapean() {
        ResponseGeneraXml src = new ResponseGeneraXml();
        src.setNumeroTicket("tkt-X");
        src.setResultadoPresentacion("0");
        src.setCodigoRespuesta("0");
        src.setDescripcionRespuesta("desc");
        src.setCodigoExcepcion(null);
        src.setDescripcionExcepcion(null);
        src.setDescripcionMensaje("msg");
        src.setEstadoProceso("ACEPTADO");
        src.setFechaRecepcion("2026-08-30T10:30:05");
        src.setValorResumen("hash123");
        src.setCodigoBarras("|barras|");
        src.setCodigoMensaje("0");
        src.setFirmaDigital("firma");
        src.setConstanciaRecepcion("Y29uc3RhbmNpYQ==");
        src.setDocumentoFirmado("PD94bWw=");
        src.setRepresentacionImpresa("SlZCRVI=");
        src.setObservaciones(Arrays.asList("obs1"));
        when(betaClient.envioSunat(any())).thenReturn(GrtCallResult.ok(src));

        NotaCreditoSendResult r = service.send(xmlValido(), "20600520033-07-F001-1.xml", false);

        assertTrue(r.isSuccess());
        assertEquals("tkt-X", r.getNumeroTicket());
        assertEquals("desc", r.getDescripcionRespuesta());
        assertEquals("hash123", r.getValorResumen());
        assertEquals("|barras|", r.getCodigoBarras());
        assertEquals("Y29uc3RhbmNpYQ==", r.getConstanciaRecepcion());
        assertEquals("PD94bWw=", r.getDocumentoFirmado());
        assertEquals("SlZCRVI=", r.getRepresentacionImpresa());
        assertNotNull(r.getObservaciones());
        assertEquals(1, r.getObservaciones().size());
    }

    @Test
    public void filenameNullOFinalVacio_seUsaDefault() {
        when(betaClient.envioSunat(any())).thenReturn(GrtCallResult.ok(responseOk()));

        NotaCreditoSendResult r = service.send(xmlValido(), null, false);
        assertEquals("nota-credito.xml", r.getFilename());

        NotaCreditoSendResult r2 = service.send(xmlValido(), "   ", false);
        assertEquals("nota-credito.xml", r2.getFilename());
    }

    @Test
    public void descripcionExcepcionNullPeroRespuestaNoCero_mapeaSunatRejectedConDescripcionRespuesta() {
        ResponseGeneraXml src = new ResponseGeneraXml();
        src.setCodigoRespuesta("9999");
        src.setDescripcionRespuesta("rechazo simple");
        src.setDescripcionExcepcion(null);
        when(betaClient.envioSunat(any())).thenReturn(GrtCallResult.ok(src));

        NotaCreditoSendResult r = service.send(xmlValido(), "x.xml", false);

        assertFalse(r.isSuccess());
        assertEquals("SUNAT_REJECTED", r.getErrorCode());
        assertEquals("rechazo simple", r.getErrorMessage());
        assertEquals("9999", r.getCodigoRespuesta());
        assertNull(r.getCodigoExcepcion());
    }
}