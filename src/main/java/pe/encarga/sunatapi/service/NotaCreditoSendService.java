package pe.encarga.sunatapi.service;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;

import pe.encarga.sunatapi.service.grt.GrtCallResult;
import pe.encarga.sunatapi.service.grt.GrtClient;
import pe.encarga.sunatapi.service.grt.ResponseGeneraXml;

@Service
public class NotaCreditoSendService {

    private static final Log log = LogFactory.getLog(NotaCreditoSendService.class);

    private final GrtClient prdClient;
    private final GrtClient betaClient;

    @Autowired
    public NotaCreditoSendService(@Qualifier("grtPrdClient") GrtClient prdClient,
                                  @Qualifier("grtBetaClient") GrtClient betaClient) {
        this.prdClient = prdClient;
        this.betaClient = betaClient;
    }

    public NotaCreditoSendResult send(byte[] xmlFirmado, String filename, boolean production) {
        NotaCreditoSendResult result = new NotaCreditoSendResult();
        String resolvedFilename = filename == null || filename.trim().isEmpty() ? "nota-credito.xml" : filename.trim();
        result.setFilename(resolvedFilename);
        result.setEnvironment(production ? "PRD" : "BETA");

        if (!isValidXml(xmlFirmado)) {
            result.setSuccess(false);
            result.setErrorCode("INVALID_XML");
            result.setErrorMessage("El archivo enviado no es un XML valido o esta vacio.");
            return result;
        }

        GrtClient client = production ? prdClient : betaClient;
        GrtCallResult call = client.envioSunat(xmlFirmado);

        if (!call.isSuccess()) {
            result.setSuccess(false);
            result.setErrorCode(call.getErrorCode());
            result.setErrorMessage(call.getErrorMessage());
            log.warn(String.format("Envio NC FAIL [%s] archivo=%s error=%s mensaje=%s",
                    result.getEnvironment(), resolvedFilename,
                    call.getErrorCode(), call.getErrorMessage()));
            return result;
        }

        applyResponse(result, call.getResponse());

        if (!result.isSuccess()) {
            result.setErrorCode("SUNAT_REJECTED");
            result.setErrorMessage(result.getDescripcionExcepcion() != null
                    ? result.getDescripcionExcepcion()
                    : result.getDescripcionRespuesta());
            log.warn(String.format("Envio NC REJECTED [%s] archivo=%s codigoRespuesta=%s codigoExcepcion=%s",
                    result.getEnvironment(), resolvedFilename,
                    result.getCodigoRespuesta(), result.getCodigoExcepcion()));
        } else {
            log.info(String.format("Envio NC OK [%s] archivo=%s ticket=%s codigoRespuesta=%s",
                    result.getEnvironment(), resolvedFilename,
                    result.getNumeroTicket(), result.getCodigoRespuesta()));
        }

        return result;
    }

    private void applyResponse(NotaCreditoSendResult target, ResponseGeneraXml src) {
        if (src == null) {
            return;
        }
        target.setNumeroTicket(src.getNumeroTicket());
        target.setResultadoPresentacion(src.getResultadoPresentacion());
        target.setCodigoRespuesta(src.getCodigoRespuesta());
        target.setDescripcionRespuesta(src.getDescripcionRespuesta());
        target.setCodigoExcepcion(src.getCodigoExcepcion());
        target.setDescripcionExcepcion(src.getDescripcionExcepcion());
        target.setDescripcionMensaje(src.getDescripcionMensaje());
        target.setEstadoProceso(src.getEstadoProceso());
        target.setFechaRecepcion(src.getFechaRecepcion());
        target.setValorResumen(src.getValorResumen());
        target.setCodigoBarras(src.getCodigoBarras());
        target.setCodigoMensaje(src.getCodigoMensaje());
        target.setFirmaDigital(src.getFirmaDigital());
        target.setConstanciaRecepcion(src.getConstanciaRecepcion());
        target.setDocumentoFirmado(src.getDocumentoFirmado());
        target.setRepresentacionImpresa(src.getRepresentacionImpresa());
        target.setObservaciones(src.getObservaciones());

        boolean aceptado = "0".equals(src.getCodigoRespuesta()) && src.getCodigoExcepcion() == null;
        target.setSuccess(aceptado);
    }

    private static boolean isValidXml(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            DocumentBuilder builder = dbf.newDocumentBuilder();
            builder.parse(new InputSource(new ByteArrayInputStream(bytes)));
            return true;
        } catch (Exception ex) {
            log.warn("XML invalido enviado a GRT: " + ex.getMessage());
            return false;
        }
    }
}