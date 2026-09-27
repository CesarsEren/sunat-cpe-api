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

    /** Username SOL (RUC + MODDATOS). Viene de variable de entorno SOL_USER. */
    @Value("${sunat.sol.user:}")
    private String solUser;

    /** Password SOL. Viene de variable de entorno SOL_PASSWORD. */
    @Value("${sunat.sol.password:}")
    private String solPassword;

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

    public String getSolUser() {
        return solUser;
    }

    public void setSolUser(String solUser) {
        this.solUser = solUser;
    }

    public String getSolPassword() {
        return solPassword;
    }

    public void setSolPassword(String solPassword) {
        this.solPassword = solPassword;
    }
}