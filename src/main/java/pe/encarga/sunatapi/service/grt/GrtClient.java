package pe.encarga.sunatapi.service.grt;

public interface GrtClient {

    String getEnvironment();

    GrtCallResult envioSunat(byte[] xmlFirmado);
}