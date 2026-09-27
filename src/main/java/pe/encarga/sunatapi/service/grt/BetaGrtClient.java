package pe.encarga.sunatapi.service.grt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import pe.encarga.sunatapi.config.GrtProperties;

@Service("grtBetaClient")
public class BetaGrtClient extends GrtHttpClient {

    @Autowired
    public BetaGrtClient(GrtProperties props,
                         @Qualifier("grtRestTemplate") RestTemplate restTemplate) {
        super(props.getPathEnviarBetta(), props.getUser(), props.getPass(), restTemplate);
    }

    @Override
    public String getEnvironment() {
        return "BETA";
    }
}