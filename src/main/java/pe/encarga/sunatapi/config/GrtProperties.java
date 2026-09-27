package pe.encarga.sunatapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.api.grt")
public class GrtProperties {

    private String pathEnviarBetta;
    private String pathEnviarPrd;
    private String ruc;
    private String user;
    private String pass;
    private int connectTimeoutMs = 10000;
    private int requestTimeoutMs = 60000;

    public String getPathEnviarBetta() {
        return pathEnviarBetta;
    }

    public void setPathEnviarBetta(String pathEnviarBetta) {
        this.pathEnviarBetta = pathEnviarBetta;
    }

    public String getPathEnviarPrd() {
        return pathEnviarPrd;
    }

    public void setPathEnviarPrd(String pathEnviarPrd) {
        this.pathEnviarPrd = pathEnviarPrd;
    }

    public String getRuc() {
        return ruc;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
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