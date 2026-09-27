package pe.encarga.sunatapi;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import pe.encarga.sunatapi.service.PrdSoapClient;
import pe.encarga.sunatapi.service.BetaSoapClient;
import pe.encarga.sunatapi.service.SunatSoapClient;
import pe.encarga.sunatapi.service.SunatSendService;
import pe.encarga.sunatapi.service.CdrParser;
import pe.encarga.sunatapi.config.SunatProperties;

import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SunatCpeApiApplicationTests {

    @Autowired
    private SunatSendService sendService;

    @Autowired
    private SunatProperties properties;

    @Autowired
    private CdrParser cdrParser;

    @Autowired
    @Qualifier("prdSoapClient")
    private SunatSoapClient prdClient;

    @Autowired
    @Qualifier("betaSoapClient")
    private SunatSoapClient betaClient;

    @Test
    public void contextCargaYBeansInyectados() {
        assertNotNull(sendService);
        assertNotNull(properties);
        assertNotNull(cdrParser);
        assertNotNull(prdClient);
        assertNotNull(betaClient);
        assertNotNull("El cliente PRD debe ser instancia de PrdSoapClient", (PrdSoapClient) prdClient);
        assertNotNull("El cliente BETA debe ser instancia de BetaSoapClient", (BetaSoapClient) betaClient);
    }

    @Test
    public void clientePrdRetornaPrd() {
        assertNotNull(prdClient);
        org.junit.Assert.assertEquals("PRD", prdClient.getEnvironment());
    }

    @Test
    public void clienteBetaRetornaBeta() {
        assertNotNull(betaClient);
        org.junit.Assert.assertEquals("BETA", betaClient.getEnvironment());
    }
}