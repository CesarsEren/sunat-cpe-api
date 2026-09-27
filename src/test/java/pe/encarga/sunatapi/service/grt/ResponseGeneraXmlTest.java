package pe.encarga.sunatapi.service.grt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ResponseGeneraXmlTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    public void deserializaJsonCompletoDelDoc() throws Exception {
        String json = "{" +
                "\"numeroTicket\":\"20260830000001\"," +
                "\"resultadoPresentacion\":\"0\"," +
                "\"codigoRespuesta\":\"0\"," +
                "\"descripcionRespuesta\":\"La Nota de Credito F001-1 ha sido aceptada\"," +
                "\"constanciaRecepcion\":\"UEsDBAoAAAAA...\"," +
                "\"descripcionExcepcion\":null," +
                "\"descripcionMensaje\":\"Documento Generado\"," +
                "\"documentoFirmado\":\"PD94bWwgdmVyc2lvbj0...\"," +
                "\"codigoExcepcion\":null," +
                "\"representacionImpresa\":\"JVBERi0xLjQKJe...\"," +
                "\"fechaRecepcion\":\"2026-08-30T10:30:05\"," +
                "\"valorResumen\":\"a1b2c3d4...\"," +
                "\"codigoBarras\":\"|20559109879|07|F001|...|\"," +
                "\"estadoProceso\":\"ACEPTADO\"," +
                "\"firmaDigital\":\"MIAGCSqGSIb3DQEHAqCAMIACAQEx...\"," +
                "\"codigoMensaje\":\"0\"," +
                "\"observaciones\":[]" +
                "}";

        ResponseGeneraXml r = mapper.readValue(json, ResponseGeneraXml.class);

        assertNotNull(r);
        assertEquals("20260830000001", r.getNumeroTicket());
        assertEquals("0", r.getResultadoPresentacion());
        assertEquals("0", r.getCodigoRespuesta());
        assertEquals("La Nota de Credito F001-1 ha sido aceptada", r.getDescripcionRespuesta());
        assertEquals("UEsDBAoAAAAA...", r.getConstanciaRecepcion());
        assertNull(r.getDescripcionExcepcion());
        assertEquals("Documento Generado", r.getDescripcionMensaje());
        assertEquals("PD94bWwgdmVyc2lvbj0...", r.getDocumentoFirmado());
        assertNull(r.getCodigoExcepcion());
        assertEquals("JVBERi0xLjQKJe...", r.getRepresentacionImpresa());
        assertEquals("2026-08-30T10:30:05", r.getFechaRecepcion());
        assertEquals("a1b2c3d4...", r.getValorResumen());
        assertEquals("|20559109879|07|F001|...|", r.getCodigoBarras());
        assertEquals("ACEPTADO", r.getEstadoProceso());
        assertEquals("MIAGCSqGSIb3DQEHAqCAMIACAQEx...", r.getFirmaDigital());
        assertEquals("0", r.getCodigoMensaje());
        assertNotNull(r.getObservaciones());
        assertTrue(r.getObservaciones().isEmpty());
    }

    @Test
    public void deserializaJsonConObservacionesYNulos() throws Exception {
        String json = "{" +
                "\"numeroTicket\":\"tkt\"," +
                "\"codigoRespuesta\":\"2300\"," +
                "\"descripcionExcepcion\":\"El comprobante ya fue registrado\"," +
                "\"codigoExcepcion\":\"001\"," +
                "\"observaciones\":[\"obs1\",\"obs2\"]," +
                "\"constanciaRecepcion\":null" +
                "}";

        ResponseGeneraXml r = mapper.readValue(json, ResponseGeneraXml.class);

        assertEquals("tkt", r.getNumeroTicket());
        assertEquals("2300", r.getCodigoRespuesta());
        assertEquals("El comprobante ya fue registrado", r.getDescripcionExcepcion());
        assertEquals("001", r.getCodigoExcepcion());
        List<String> obs = r.getObservaciones();
        assertNotNull(obs);
        assertEquals(2, obs.size());
        assertEquals("obs1", obs.get(0));
        assertEquals("obs2", obs.get(1));
        assertNull(r.getConstanciaRecepcion());
        assertNull(r.getDescripcionRespuesta());
        assertNull(r.getRepresentacionImpresa());
    }

    @Test
    public void deserializaJsonVacio_devuelveObjetoConCamposNulos() throws Exception {
        ResponseGeneraXml r = mapper.readValue("{}", ResponseGeneraXml.class);

        assertNotNull(r);
        assertNull(r.getNumeroTicket());
        assertNull(r.getCodigoRespuesta());
        assertNull(r.getObservaciones());
    }

    @Test
    public void ignoraCamposDesconocidos() throws Exception {
        String json = "{\"campoNuevo\":\"valor\",\"numeroTicket\":\"tkt-1\"}";
        ResponseGeneraXml r = mapper.readValue(json, ResponseGeneraXml.class);
        assertEquals("tkt-1", r.getNumeroTicket());
    }

    @Test
    public void serializaRedondeaComoEsperado() throws Exception {
        ResponseGeneraXml r = new ResponseGeneraXml();
        r.setNumeroTicket("t1");
        r.setCodigoRespuesta("0");
        r.setEstadoProceso("ACEPTADO");
        r.setObservaciones(Arrays.asList("obs"));

        String json = mapper.writeValueAsString(r);
        assertTrue(json.contains("\"numeroTicket\":\"t1\""));
        assertTrue(json.contains("\"codigoRespuesta\":\"0\""));
        assertTrue(json.contains("\"estadoProceso\":\"ACEPTADO\""));
        assertTrue(json.contains("\"observaciones\":[\"obs\"]"));
    }
}