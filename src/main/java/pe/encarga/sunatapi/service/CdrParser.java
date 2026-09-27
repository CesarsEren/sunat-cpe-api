package pe.encarga.sunatapi.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Component
public class CdrParser {

    private static final Log log = LogFactory.getLog(CdrParser.class);

    static final int MAX_DESCRIPTION_LENGTH = 500;
    static final int MAX_CDR_PREVIEW_LENGTH = 300;

    public CdrParseResult parse(byte[] responseZip) {
        if (responseZip == null || responseZip.length == 0) {
            return CdrParseResult.error("EMPTY_RESPONSE", "SUNAT devolvio una respuesta vacia.");
        }

        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new ByteArrayInputStream(responseZip));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int len;
                while ((len = zis.read(buf)) > 0) {
                    baos.write(buf, 0, len);
                }
                String xml = new String(baos.toByteArray(), "UTF-8");
                return parseXml(xml);
            }
            return CdrParseResult.error("CDR_PARSE_ERROR", "El ZIP-respuesta no contiene entradas XML.");
        } catch (Exception ex) {
            return CdrParseResult.error("CDR_PARSE_ERROR",
                    "No se pudo leer/parsear el ZIP-respuesta: " + ex.getMessage());
        } finally {
            closeQuietly(zis);
        }
    }

    CdrParseResult parseXml(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
            Document doc = documentBuilder.parse(new InputSource(new StringReader(xml)));

            NodeList codeList = doc.getElementsByTagName("cbc:ResponseCode");
            NodeList descList = doc.getElementsByTagName("cbc:Description");

            if (codeList.getLength() == 0) {
                codeList = doc.getElementsByTagName("ResponseCode");
            }
            if (descList.getLength() == 0) {
                descList = doc.getElementsByTagName("Description");
            }

            if (codeList.getLength() == 0 || descList.getLength() == 0) {
                return CdrParseResult.error("CDR_INVALID_FORMAT",
                        "La respuesta de SUNAT no contiene ResponseCode/Description. CDR (primeros "
                                + MAX_CDR_PREVIEW_LENGTH + " chars): " + truncate(xml, MAX_CDR_PREVIEW_LENGTH));
            }

            Node code = codeList.item(0);
            Node desc = descList.item(0);

            String codeText = code.getTextContent().trim();
            String descText = desc.getTextContent().trim();

            String description = truncate(descText, MAX_DESCRIPTION_LENGTH);
            if ("0".equals(codeText)) {
                return CdrParseResult.ok(codeText, description);
            }
            return CdrParseResult.rejected(codeText, description);

        } catch (Exception ex) {
            log.error("Error al parsear XML del CDR", ex);
            return CdrParseResult.error("CDR_XML_PARSE_ERROR",
                    "No se pudo parsear el XML del CDR: " + ex.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static void closeQuietly(ZipInputStream zis) {
        if (zis != null) {
            try {
                zis.close();
            } catch (Exception e) {
                log.warn("No se pudo cerrar ZipInputStream", e);
            }
        }
    }
}