package pe.encarga.sunatapi.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida y parsea el nombre del archivo ZIP de un CPE enviado a SUNAT.
 *
 * Formato esperado: {@code RUC-TIPO-SERIE-NUMERO.zip}
 * <ul>
 *   <li>RUC: 11 dígitos</li>
 *   <li>TIPO: 01 (Factura), 03 (Boleta), 05 (Nota de Débito), 06 (Nota de Crédito)</li>
 *   <li>SERIE: 4 caracteres alfanuméricos en mayúscula (ej. F001, B001)</li>
 *   <li>NUMERO: 1 a 8 dígitos</li>
 * </ul>
 *
 * Centralizado aquí para evitar duplicar la regex en SunatSendService y NotaCreditoSendService,
 * y para mantener una única fuente de verdad sobre los tipos de CPE soportados.
 */
public final class ZipNameValidator {

    /** Catálogo SUNAT de tipos de CPE aceptados en el nombre del zip. */
    public static final class TipoCpe {
        public static final String FACTURA       = "01";
        public static final String BOLETA        = "03";
        public static final String NOTA_DEBITO   = "05";
        public static final String NOTA_CREDITO  = "06";
        private TipoCpe() {}
    }

    private static final String TIPO_PATTERN =
            TipoCpe.FACTURA + "|" + TipoCpe.BOLETA + "|" + TipoCpe.NOTA_DEBITO + "|" + TipoCpe.NOTA_CREDITO;

    private static final Pattern ZIP_NAME_PATTERN =
            Pattern.compile("^(\\d{11})-(" + TIPO_PATTERN + ")-([A-Z0-9]{4})-(\\d{1,8})\\.zip$");

    private ZipNameValidator() {}

    public static boolean isValid(String filename) {
        return filename != null && ZIP_NAME_PATTERN.matcher(filename).matches();
    }

    /**
     * Mapea el tipo de CPE interno (UBL 2.1) al código usado en el nombre del zip SUNAT.
     *
     * <p>En UBL 2.1 una NC se identifica como {@code 07}, pero el filename esperado
     * por SUNAT para el zip la identifica como {@code 06}.</p>
     *
     * @param tipoDocumentoInterno código del CPE según UBL 2.1 (01, 03, 05, 07, 08, 09)
     * @return código usado en el filename del zip (06 para NC, 07 para ND, etc.) o
     *         el mismo {@code tipoDocumentoInterno} si no requiere mapeo.
     */
    public static String tipoInternoAZip(String tipoDocumentoInterno) {
        if (tipoDocumentoInterno == null) return null;
        switch (tipoDocumentoInterno) {
            case "07": return TipoCpe.NOTA_CREDITO;
            case "08": return TipoCpe.NOTA_DEBITO;
            default:   return tipoDocumentoInterno;
        }
    }

    /**
     * Construye el nombre canónico del zip a partir del RUC y los datos del CPE.
     *
     * @param ruc                 RUC emisor (11 dígitos)
     * @param tipoDocumentoInterno tipo del CPE según UBL 2.1
     * @param serie               serie del documento (ej. F001)
     * @param numero              correlativo (1 a 8 dígitos)
     * @return nombre del zip (ej. {@code 20600520033-06-F001-00000001.zip})
     */
    public static String buildZipName(String ruc, String tipoDocumentoInterno, String serie, String numero) {
        return ruc + "-" + tipoInternoAZip(tipoDocumentoInterno) + "-" + serie + "-" + numero + ".zip";
    }

    /**
     * Extrae los componentes del nombre del zip. Retorna null si no es válido.
     */
    public static ParsedZip parse(String filename) {
        if (filename == null) return null;
        Matcher m = ZIP_NAME_PATTERN.matcher(filename);
        if (!m.matches()) return null;
        return new ParsedZip(m.group(1), m.group(2), m.group(3), m.group(4));
    }

    /** Componentes parseados de un nombre de zip válido. */
    public static final class ParsedZip {
        private final String ruc;
        private final String tipo;
        private final String serie;
        private final String numero;
        public ParsedZip(String ruc, String tipo, String serie, String numero) {
            this.ruc = ruc; this.tipo = tipo; this.serie = serie; this.numero = numero;
        }
        public String getRuc()    { return ruc; }
        public String getTipo()   { return tipo; }
        public String getSerie()  { return serie; }
        public String getNumero() { return numero; }
    }
}
