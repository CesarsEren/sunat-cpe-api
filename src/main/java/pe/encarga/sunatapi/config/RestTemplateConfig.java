package pe.encarga.sunatapi.config;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Autowired
    private GrtProperties grtProperties;

    @Bean("grtRestTemplate")
    public RestTemplate grtRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TimeUnit.MILLISECONDS.toMillis(grtProperties.getConnectTimeoutMs()));
        factory.setReadTimeout((int) TimeUnit.MILLISECONDS.toMillis(grtProperties.getRequestTimeoutMs()));
        return new RestTemplate(factory);
    }
}