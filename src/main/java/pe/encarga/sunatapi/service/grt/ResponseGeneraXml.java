package pe.encarga.sunatapi.service.grt;

import java.util.List;

public class ResponseGeneraXml {

    private String numeroTicket;
    private String resultadoPresentacion;
    private String codigoRespuesta;
    private String descripcionRespuesta;
    private String constanciaRecepcion;
    private String descripcionExcepcion;
    private String descripcionMensaje;
    private String documentoFirmado;
    private String codigoExcepcion;
    private String representacionImpresa;
    private String fechaRecepcion;
    private String valorResumen;
    private String codigoBarras;
    private String estadoProceso;
    private String firmaDigital;
    private String codigoMensaje;
    private List<String> observaciones;

    public String getNumeroTicket() {
        return numeroTicket;
    }

    public void setNumeroTicket(String numeroTicket) {
        this.numeroTicket = numeroTicket;
    }

    public String getResultadoPresentacion() {
        return resultadoPresentacion;
    }

    public void setResultadoPresentacion(String resultadoPresentacion) {
        this.resultadoPresentacion = resultadoPresentacion;
    }

    public String getCodigoRespuesta() {
        return codigoRespuesta;
    }

    public void setCodigoRespuesta(String codigoRespuesta) {
        this.codigoRespuesta = codigoRespuesta;
    }

    public String getDescripcionRespuesta() {
        return descripcionRespuesta;
    }

    public void setDescripcionRespuesta(String descripcionRespuesta) {
        this.descripcionRespuesta = descripcionRespuesta;
    }

    public String getConstanciaRecepcion() {
        return constanciaRecepcion;
    }

    public void setConstanciaRecepcion(String constanciaRecepcion) {
        this.constanciaRecepcion = constanciaRecepcion;
    }

    public String getDescripcionExcepcion() {
        return descripcionExcepcion;
    }

    public void setDescripcionExcepcion(String descripcionExcepcion) {
        this.descripcionExcepcion = descripcionExcepcion;
    }

    public String getDescripcionMensaje() {
        return descripcionMensaje;
    }

    public void setDescripcionMensaje(String descripcionMensaje) {
        this.descripcionMensaje = descripcionMensaje;
    }

    public String getDocumentoFirmado() {
        return documentoFirmado;
    }

    public void setDocumentoFirmado(String documentoFirmado) {
        this.documentoFirmado = documentoFirmado;
    }

    public String getCodigoExcepcion() {
        return codigoExcepcion;
    }

    public void setCodigoExcepcion(String codigoExcepcion) {
        this.codigoExcepcion = codigoExcepcion;
    }

    public String getRepresentacionImpresa() {
        return representacionImpresa;
    }

    public void setRepresentacionImpresa(String representacionImpresa) {
        this.representacionImpresa = representacionImpresa;
    }

    public String getFechaRecepcion() {
        return fechaRecepcion;
    }

    public void setFechaRecepcion(String fechaRecepcion) {
        this.fechaRecepcion = fechaRecepcion;
    }

    public String getValorResumen() {
        return valorResumen;
    }

    public void setValorResumen(String valorResumen) {
        this.valorResumen = valorResumen;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public void setCodigoBarras(String codigoBarras) {
        this.codigoBarras = codigoBarras;
    }

    public String getEstadoProceso() {
        return estadoProceso;
    }

    public void setEstadoProceso(String estadoProceso) {
        this.estadoProceso = estadoProceso;
    }

    public String getFirmaDigital() {
        return firmaDigital;
    }

    public void setFirmaDigital(String firmaDigital) {
        this.firmaDigital = firmaDigital;
    }

    public String getCodigoMensaje() {
        return codigoMensaje;
    }

    public void setCodigoMensaje(String codigoMensaje) {
        this.codigoMensaje = codigoMensaje;
    }

    public List<String> getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(List<String> observaciones) {
        this.observaciones = observaciones;
    }
}