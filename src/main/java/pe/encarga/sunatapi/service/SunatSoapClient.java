package pe.encarga.sunatapi.service;

import java.io.File;

public interface SunatSoapClient {

    String getEnvironment();

    byte[] sendBill(String filename, File zipFile, String username, String password, String partyType);

    SunatStatusResult getStatus(String ticket, String username, String password);
}