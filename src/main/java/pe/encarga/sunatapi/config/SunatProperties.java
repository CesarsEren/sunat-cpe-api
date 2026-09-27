package pe.encarga.sunatapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SunatProperties {

    @Value("${sunat.production:false}")
    private boolean defaultProduction;

    @Value("${sunat.soap.connect.timeout.ms:15000}")
    private int connectTimeoutMs;

    @Value("${sunat.soap.request.timeout.ms:60000}")
    private int requestTimeoutMs;

    public boolean isDefaultProduction() {
        return defaultProduction;
    }

    public void setDefaultProduction(boolean defaultProduction) {
        this.defaultProduction = defaultProduction;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }
}