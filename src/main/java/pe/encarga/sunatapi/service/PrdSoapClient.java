package pe.encarga.sunatapi.service;

import java.io.File;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.springframework.stereotype.Service;

import pe.encarga.sunatapi.soap.prd.BillService;
import pe.encarga.sunatapi.soap.prd.BillService_Service;
import pe.encarga.sunatapi.soap.prd.StatusResponse;
import pe.encarga.sunatapi.ws.WsCredentials;
import pe.encarga.sunatapi.ws.WsHeaderHandlerResolver;

@Service("prdSoapClient")
public class PrdSoapClient implements SunatSoapClient {

    @Override
    public String getEnvironment() {
        return "PRD";
    }

    @Override
    public byte[] sendBill(String filename, File zipFile, String username, String password, String partyType) {
        BillService_Service servicePort = new BillService_Service();
        servicePort.setHandlerResolver(new WsHeaderHandlerResolver(new WsCredentials(username, password)));
        BillService billService = servicePort.getBillServicePort();
        DataHandler dh = new DataHandler(new FileDataSource(zipFile));
        String pt = partyType == null ? "" : partyType;
        return billService.sendBill(filename, dh, pt);
    }

    @Override
    public SunatStatusResult getStatus(String ticket, String username, String password) {
        BillService_Service servicePort = new BillService_Service();
        servicePort.setHandlerResolver(new WsHeaderHandlerResolver(new WsCredentials(username, password)));
        BillService billService = servicePort.getBillServicePort();
        StatusResponse resp = billService.getStatus(ticket);
        SunatStatusResult result = new SunatStatusResult();
        result.setTicket(ticket);
        result.setEnvironment(getEnvironment());
        result.setStatusCode(safe(resp.getStatusCode()));
        if (resp.getContent() != null) {
            result.setStatusMessage("(content en Base64, " + resp.getContent().length + " bytes)");
        }
        result.setSuccess(true);
        return result;
    }

    private static String safe(String s) {
        return s == null ? null : s.trim();
    }
}