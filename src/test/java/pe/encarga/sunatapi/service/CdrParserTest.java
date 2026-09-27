package pe.encarga.sunatapi.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Before;
import org.junit.Test;

public class CdrParserTest {

    private CdrParser parser;

    @Before
    public void setUp() {
        parser = new CdrParser();
    }

    @Test
    public void parse_xmlOkConPrefijoCbcc_devuelveSuccess() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ApplicationResponse xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\" "
                + "xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
                + "<cac:DocumentResponse xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\">"
                + "<cbc:ResponseCode>0</cbc:ResponseCode>"
                + "<cbc:Description>La Factura F001-1 fue registrada</cbc:Description>"
                + "</cac:DocumentResponse></ApplicationResponse>";
        byte[] zip = zipSingleEntry("R-20600520033-01-F001-1.xml", xml);

        CdrParseResult result = parser.parse(zip);

        assertNotNull(result);
        assertTrue("Debio ser exitoso con responseCode=0", result.isSuccess());
        assertEquals("0", result.getResponseCode());
        assertEquals("La Factura F001-1 fue registrada", result.getDescription());
        assertNull(result.getErrorCode());
        assertNull(result.getErrorMessage());
    }

    @Test
    public void parse_xmlOkSinPrefijo_tambienDevuelveSuccess() {
        String xml = "<ApplicationResponse>"
                + "<DocumentResponse>"
                + "<ResponseCode>0</ResponseCode>"
                + "<Description>La Boleta B001-1 fue registrada</Description>"
                + "</DocumentResponse></ApplicationResponse>";
        byte[] zip = zipSingleEntry("R-boleta.xml", xml);

        CdrParseResult result = parser.parse(zip);

        assertTrue(result.isSuccess());
        assertEquals("0", result.getResponseCode());
        assertEquals("La Boleta B001-1 fue registrada", result.getDescription());
    }

    @Test
    public void parse_responseCodeNoCero_devuelveRejected() {
        String xml = "<?xml version=\"1.0\"?>"
                + "<ApplicationResponse xmlns:cbc=\"urn:foo\">"
                + "<cbc:ResponseCode>2300</cbc:ResponseCode>"
                + "<cbc:Description>El comprobante ya fue registrado anteriormente</cbc:Description>"
                + "</ApplicationResponse>";
        byte[] zip = zipSingleEntry("R-20600520033-01-F001-1.xml", xml);

        CdrParseResult result = parser.parse(zip);

        assertFalse(result.isSuccess());
        assertEquals("2300", result.getResponseCode());
        assertEquals("El comprobante ya fue registrado anteriormente", result.getDescription());
        assertEquals("SUNAT_REJECTED", result.getErrorCode());
        assertEquals("El comprobante ya fue registrado anteriormente", result.getErrorMessage());
    }

    @Test
    public void parse_zipVacio_devuelveEmptyResponseError() {
        CdrParseResult result = parser.parse(new byte[0]);

        assertFalse(result.isSuccess());
        assertEquals("EMPTY_RESPONSE", result.getErrorCode());
        assertEquals("SUNAT devolvio una respuesta vacia.", result.getErrorMessage());
        assertNull(result.getResponseCode());
        assertNull(result.getDescription());
    }

    @Test
    public void parse_null_devuelveEmptyResponseError() {
        CdrParseResult result = parser.parse(null);

        assertFalse(result.isSuccess());
        assertEquals("EMPTY_RESPONSE", result.getErrorCode());
    }

    @Test
    public void parse_zipConEntryVacia_devuelveCdrXmlParseError() {
        byte[] zipWithEmptyEntry = zipSingleEntry("dummy.txt", "");
        CdrParseResult result = parser.parse(zipWithEmptyEntry);

        assertFalse(result.isSuccess());
        assertEquals("CDR_XML_PARSE_ERROR", result.getErrorCode());
    }

    @Test
    public void parse_xmlSinResponseCode_devuelveCdrInvalidFormat() {
        String xml = "<ApplicationResponse><OtroTag>valor</OtroTag></ApplicationResponse>";
        byte[] zip = zipSingleEntry("R-x.xml", xml);

        CdrParseResult result = parser.parse(zip);

        assertFalse(result.isSuccess());
        assertEquals("CDR_INVALID_FORMAT", result.getErrorCode());
        assertTrue("Mensaje debe incluir preview del CDR",
                result.getErrorMessage().contains("La respuesta de SUNAT"));
    }

    @Test
    public void parse_xmlMalFormado_devuelveCdrXmlParseError() {
        byte[] zip = zipSingleEntry("R-x.xml", "esto no es XML <<<");

        CdrParseResult result = parser.parse(zip);

        assertFalse(result.isSuccess());
        assertEquals("CDR_XML_PARSE_ERROR", result.getErrorCode());
    }

    @Test
    public void parse_descripcionLarga_seTruncaA500Chars() {
        StringBuilder desc = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            desc.append('A');
        }
        String xml = "<ApplicationResponse>"
                + "<ResponseCode>0</ResponseCode>"
                + "<Description>" + desc.toString() + "</Description>"
                + "</ApplicationResponse>";
        byte[] zip = zipSingleEntry("R-x.xml", xml);

        CdrParseResult result = parser.parse(zip);

        assertTrue(result.isSuccess());
        assertEquals(500, result.getDescription().length());
    }

    @Test
    public void parse_cdrBytesInvalidos_devuelveCdrParseError() {
        byte[] garbage = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };

        CdrParseResult result = parser.parse(garbage);

        assertFalse(result.isSuccess());
        assertEquals("CDR_PARSE_ERROR", result.getErrorCode());
    }

    @Test
    public void parse_xmlConVariasEntradasTomaLaPrimeraConDatos() {
        String first = "<ApplicationResponse>"
                + "<ResponseCode>0</ResponseCode>"
                + "<Description>OK primera entrada</Description>"
                + "</ApplicationResponse>";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        try {
            putEntry(zos, "primero.xml", first);
            putEntry(zos, "segundo.xml", "<otraCosa/>");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        byte[] zip = baos.toByteArray();

        CdrParseResult result = parser.parse(zip);

        assertTrue(result.isSuccess());
        assertEquals("OK primera entrada", result.getDescription());
    }

    private static byte[] zipSingleEntry(String entryName, String content) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        try {
            putEntry(zos, entryName, content);
            zos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    private static void putEntry(ZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(content.getBytes("UTF-8"));
        zos.closeEntry();
    }
}