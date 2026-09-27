package pe.encarga.sunatapi.service;

import java.io.File;

import javax.xml.ws.soap.SOAPFaultException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SunatSendService {

    private static final Log log = LogFactory.getLog(SunatSendService.class);

    static final int MAX_DESCRIPTION_LENGTH = 500;

    private final SunatSoapClient prdClient;
    private final SunatSoapClient betaClient;
    private final CdrParser cdrParser;

    @Autowired
    public SunatSendService(@Qualifier("prdSoapClient") SunatSoapClient prdClient,
                            @Qualifier("betaSoapClient") SunatSoapClient betaClient,
                            CdrParser cdrParser) {
        this.prdClient = prdClient;
        this.betaClient = betaClient;
        this.cdrParser = cdrParser;
    }

    public SunatSendResult sendBill(File zipFile, String originalFilename, String username, String password,
                                    boolean production, String partyType) {
        SunatSendResult result = new SunatSendResult();
        String filename = originalFilename != null && !originalFilename.trim().isEmpty()
                ? originalFilename.trim()
                : zipFile.getName();
        String environment = production ? "PRD" : "BETA";
        result.setFilename(filename);
        result.setEnvironment(environment);

        if (!zipFile.exists() || !zipFile.isFile()) {
            return fail(result, "FILE_NOT_FOUND",
                    "El archivo .zip no existe o no es accesible: " + zipFile.getAbsolutePath());
        }

        if (!ZipNameValidator.isValid(filename)) {
            return fail(result, "INVALID_FILENAME",
                    "Nombre de archivo invalido. Formato esperado: "
                            + "RUC-TIPO-SERIE-NUMERO.zip (TIPO=01 Factura, 03 Boleta, 05 Nota Debito, 06 Nota Credito). "
                            + "Recibido: " + filename);
        }

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return fail(result, "MISSING_CREDENTIALS", "Debe proporcionar username y password de SOL SUNAT.");
        }

        long start = System.currentTimeMillis();
        try {
            SunatSoapClient client = production ? prdClient : betaClient;
            String effectivePartyType = production && partyType == null ? "" : partyType;
            byte[] responseZip = client.sendBill(filename, zipFile, username, password, effectivePartyType);
            result.setCdrZip(responseZip);

            CdrParseResult parsed = cdrParser.parse(responseZip);
            applyParseResult(result, parsed);

            log.info(String.format(
                    "Envio %s [%s] archivo=%s responseCode=%s duracion=%dms descripcion=%s",
                    parsed.isSuccess() ? "OK" : "REJECTED",
                    environment, filename, parsed.getResponseCode(),
                    System.currentTimeMillis() - start,
                    truncate(parsed.getDescription(), 200)));

        } catch (SOAPFaultException so) {
            String msg = truncate(so.getMessage(), MAX_DESCRIPTION_LENGTH);
            fail(result, "SOAP_FAULT", msg);
            log.error(String.format("Envio FAIL [%s] archivo=%s SOAPFault duracion=%dms mensaje=%s",
                    environment, filename, System.currentTimeMillis() - start, msg), so);

        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName()
                    : truncate(ex.getMessage(), MAX_DESCRIPTION_LENGTH);
            fail(result, ex.getClass().getSimpleName(), msg);
            log.error(String.format("Envio FAIL [%s] archivo=%s duracion=%dms error=%s",
                    environment, filename, System.currentTimeMillis() - start, msg), ex);
        }

        return result;
    }

    public SunatStatusResult getStatus(String ticket, String username, String password, boolean production) {
        SunatStatusResult result = new SunatStatusResult();
        result.setTicket(ticket);
        result.setEnvironment(production ? "PRD" : "BETA");

        if (ticket == null || ticket.trim().isEmpty()) {
            return failStatus(result, "MISSING_TICKET", "Debe proporcionar el numero de ticket.");
        }

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return failStatus(result, "MISSING_CREDENTIALS",
                    "Debe proporcionar username y password de SOL SUNAT.");
        }

        try {
            SunatSoapClient client = production ? prdClient : betaClient;
            return client.getStatus(ticket, username, password);
        } catch (SOAPFaultException so) {
            String msg = truncate(so.getMessage(), MAX_DESCRIPTION_LENGTH);
            failStatus(result, "SOAP_FAULT", msg);
            log.error(String.format("getStatus FAIL [%s] ticket=%s SOAPFault mensaje=%s",
                    result.getEnvironment(), ticket, msg), so);
            return result;
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName()
                    : truncate(ex.getMessage(), MAX_DESCRIPTION_LENGTH);
            failStatus(result, ex.getClass().getSimpleName(), msg);
            log.error(String.format("getStatus FAIL [%s] ticket=%s error=%s",
                    result.getEnvironment(), ticket, msg), ex);
            return result;
        }
    }

    private void applyParseResult(SunatSendResult target, CdrParseResult parsed) {
        target.setResponseCode(parsed.getResponseCode());
        target.setDescription(parsed.getDescription());
        target.setSuccess(parsed.isSuccess());
        if (!parsed.isSuccess()) {
            target.setErrorCode(parsed.getErrorCode());
            target.setErrorMessage(parsed.getErrorMessage());
        }
    }

    private SunatSendResult fail(SunatSendResult r, String code, String message) {
        r.setSuccess(false);
        r.setErrorCode(code);
        r.setErrorMessage(message);
        return r;
    }

    private SunatStatusResult failStatus(SunatStatusResult r, String code, String message) {
        r.setSuccess(false);
        r.setErrorCode(code);
        r.setErrorMessage(message);
        return r;
    }

    static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}