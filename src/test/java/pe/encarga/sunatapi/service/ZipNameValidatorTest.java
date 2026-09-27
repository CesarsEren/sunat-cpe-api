package pe.encarga.sunatapi.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ZipNameValidatorTest {

    private boolean matches(String name) {
        if (name == null) {
            return false;
        }
        return SunatSendService.ZIP_NAME_PATTERN.matcher(name).matches();
    }

    @Test
    public void facturaValida() {
        assertTrue(matches("20600520033-01-F001-00000001.zip"));
        assertTrue(matches("20600520033-01-F001-1.zip"));
        assertTrue(matches("20600520033-01-F001-00001234.zip"));
    }

    @Test
    public void boletaValida() {
        assertTrue(matches("20600520033-03-B001-1.zip"));
        assertTrue(matches("20600520033-03-B999-00000001.zip"));
    }

    @Test
    public void notaCreditoValida() {
        assertTrue(matches("20600520033-06-F001-1.zip"));
        assertTrue(matches("20600520033-06-B001-00000099.zip"));
    }

    @Test
    public void notaDebitoValida() {
        assertTrue(matches("20600520033-05-F001-1.zip"));
        assertTrue(matches("20600520033-05-B001-00000099.zip"));
    }

    @Test
    public void serieAlfanumericaEsValida() {
        assertTrue(matches("20600520033-01-FC01-1.zip"));
        assertTrue(matches("20600520033-01-FZ99-1.zip"));
        assertTrue(matches("20600520033-01-FA02-1.zip"));
    }

    @Test
    public void tipo02NoEsValido() {
        assertFalse(matches("20600520033-02-F001-1.zip"));
    }

    @Test
    public void tipo04NoEsValido() {
        assertFalse(matches("20600520033-04-F001-1.zip"));
    }

    @Test
    public void tipo07NoEsValido() {
        assertFalse(matches("20600520033-07-F001-1.zip"));
    }

    @Test
    public void rucMuyCortoNoEsValido() {
        assertFalse(matches("2060052003-01-F001-1.zip"));
    }

    @Test
    public void rucMuyLargoNoEsValido() {
        assertFalse(matches("206005200330-01-F001-1.zip"));
    }

    @Test
    public void serieMinusculaNoEsValida() {
        assertFalse(matches("20600520033-01-f001-1.zip"));
    }

    @Test
    public void serieSinPrefijoDocumentoNoEsValida() {
        assertFalse(matches("20600520033-01-001-1.zip"));
    }

    @Test
    public void sinExtensionZipNoEsValido() {
        assertFalse(matches("20600520033-01-F001-1"));
        assertFalse(matches("20600520033-01-F001-1.xml"));
        assertFalse(matches("20600520033-01-F001-1.ZIP"));
    }

    @Test
    public void numeroExcede8DigitosNoEsValido() {
        assertFalse(matches("20600520033-01-F001-123456789.zip"));
    }

    @Test
    public void letrasEnNumeroNoEsValido() {
        assertFalse(matches("20600520033-01-F001-A.zip"));
    }

    @Test
    public void prefijoExtraNoEsValido() {
        assertFalse(matches("X20600520033-01-F001-1.zip"));
    }

    @Test
    public void vacioNoEsValido() {
        assertFalse(matches(""));
    }

    @Test
    public void nullNoEsValido() {
        assertFalse(matches(null));
    }

    @Test
    public void rucConCaracteresNoNumericosNoEsValido() {
        assertFalse(matches("2060052003A-01-F001-1.zip"));
    }
}