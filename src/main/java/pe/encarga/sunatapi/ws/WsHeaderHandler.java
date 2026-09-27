package pe.encarga.sunatapi.ws;

import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

public class WsHeaderHandler implements SOAPHandler<SOAPMessageContext> {

    private final WsCredentials credentials;

    public WsHeaderHandler(WsCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public boolean handleMessage(SOAPMessageContext smc) {
        Boolean outboundProperty = (Boolean) smc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
        if (outboundProperty != null && outboundProperty.booleanValue()) {
            SOAPMessage message = smc.getMessage();
            try {
                SOAPEnvelope envelope = message.getSOAPPart().getEnvelope();
                SOAPHeader header = envelope.getHeader();
                if (header == null) {
                    header = envelope.addHeader();
                }

                SOAPElement security = header.addChildElement("Security", "wsse",
                        "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd");
                SOAPElement usernameToken = security.addChildElement("UsernameToken", "wsse");
                SOAPElement usernameElement = usernameToken.addChildElement("Username", "wsse");
                usernameElement.addTextNode(credentials.getUsername());

                SOAPElement passwordElement = usernameToken.addChildElement("Password", "wsse");
                passwordElement.addTextNode(credentials.getPassword());

                message.saveChanges();
            } catch (Exception e) {
                throw new RuntimeException("No se pudo construir la cabecera WS-Security", e);
            }
        }
        return true;
    }

    @Override
    public void close(MessageContext arg0) {
    }

    @Override
    public boolean handleFault(SOAPMessageContext arg0) {
        return true;
    }

    @Override
    public Set<QName> getHeaders() {
        return null;
    }
}