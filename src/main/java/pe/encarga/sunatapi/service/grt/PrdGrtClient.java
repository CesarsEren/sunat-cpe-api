package pe.encarga.sunatapi.service.grt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import pe.encarga.sunatapi.config.GrtProperties;

@Service("grtPrdClient")
public class PrdGrtClient extends GrtHttpClient {

    @Autowired
    public PrdGrtClient(GrtProperties props,
                        @Qualifier("grtRestTemplate") RestTemplate restTemplate) {
        super(props.getPathEnviarPrd(), props.getUser(), props.getPass(), restTemplate);
    }

    @Override
    public String getEnvironment() {
        return "PRD";
    }
}