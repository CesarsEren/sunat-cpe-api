package pe.encarga.sunatapi.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate configurado con los timeouts definidos en {@link SunatProperties}.
 * Antes existia un bean {@code grtRestTemplate} (eliminado): GRT ya no es
 * intermediario; el envio es directo al BillService SOAP de SUNAT.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate sunatRestTemplate(SunatProperties sunatProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TimeUnit.MILLISECONDS.toMillis(sunatProperties.getConnectTimeoutMs()));
        factory.setReadTimeout((int) TimeUnit.MILLISECONDS.toMillis(sunatProperties.getRequestTimeoutMs()));
        return new RestTemplate(factory);
    }
}
