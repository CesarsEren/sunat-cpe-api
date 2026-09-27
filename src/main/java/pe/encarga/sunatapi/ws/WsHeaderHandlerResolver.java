package pe.encarga.sunatapi.ws;

import java.util.ArrayList;
import java.util.List;

import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.HandlerResolver;
import javax.xml.ws.handler.PortInfo;

public class WsHeaderHandlerResolver implements HandlerResolver {

    private final WsCredentials credentials;

    public WsHeaderHandlerResolver(WsCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List<Handler> getHandlerChain(PortInfo arg0) {
        List<Handler> handlerChain = new ArrayList<Handler>();
        handlerChain.add(new WsHeaderHandler(credentials));
        return handlerChain;
    }
}