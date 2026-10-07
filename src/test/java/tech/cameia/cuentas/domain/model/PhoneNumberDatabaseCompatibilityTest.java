package tech.cameia.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tech.cameia.cuentas.domain.exception.InvalidPhoneNumberException;

/**
 * Comprueba la relación entre la validación por país y la restricción
 * {@code ck_cuenta_telefono_e164} de la base ({@code ^\+[1-9][0-9]{7,14}$}).
 *
 * <p>Se recorren los números de ejemplo de cada región para los tipos móvil, fijo y mixto. Lo
 * que no puede pasar es que un número aceptado no quepa en la base: el registro pasaría la
 * validación y fallaría al guardar con un error interno.</p>
 *
 * <p>Hay regiones con números válidos de siete dígitos, más cortos que el mínimo de la base.
 * Hoy se rechazan con el error de formato del celular; aceptarlos exige una migración que
 * relaje la restricción. La segunda prueba fija cuáles son, para que una versión nueva de la
 * librería que agregue otra región no pase sin que alguien lo decida.</p>
 */
class PhoneNumberDatabaseCompatibilityTest {

    /** La misma expresión de la restricción de la base. */
    private static final Pattern RESTRICCION_DE_LA_BASE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    /** Regiones con números válidos que no caben en la base: Tristan da Cunha, Tokelau y Niue. */
    private static final Set<String> REGIONES_FUERA_DE_LA_BASE = Set.of("TA", "TK", "NU");

    private static final List<String> EJEMPLOS = new ArrayList<>();

    @BeforeAll
    static void reunirLosEjemplos() {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        for (String region : util.getSupportedRegions()) {
            for (PhoneNumberType tipo : List.of(PhoneNumberType.MOBILE, PhoneNumberType.FIXED_LINE,
                    PhoneNumberType.FIXED_LINE_OR_MOBILE)) {
                Phonenumber.PhoneNumber ejemplo = util.getExampleNumberForType(region, tipo);
                if (ejemplo != null) {
                    EJEMPLOS.add(region + " " + util.format(ejemplo, PhoneNumberFormat.E164));
                }
            }
        }
    }

    @Test
    void todoNumeroQueSeAceptaCabeEnLaRestriccionDeLaBase() {
        List<String> aceptadosQueNoCaben = new ArrayList<>();
        int aceptados = 0;
        for (String ejemplo : EJEMPLOS) {
            String numero = ejemplo.substring(ejemplo.indexOf(' ') + 1);
            try {
                PhoneNumber.fromInput(numero);
                aceptados++;
                if (!RESTRICCION_DE_LA_BASE.matcher(numero).matches()) {
                    aceptadosQueNoCaben.add(ejemplo);
                }
            } catch (InvalidPhoneNumberException rechazado) {
                // Un número rechazado nunca llega a la base: no es parte de esta comprobación.
            }
        }

        assertThat(aceptados).isGreaterThan(400);
        assertThat(aceptadosQueNoCaben).isEmpty();
    }

    @Test
    void lasRegionesConNumerosValidosMasCortosQueLaBaseSonLasConocidas() {
        Set<String> regiones = new TreeSet<>();
        for (String ejemplo : EJEMPLOS) {
            String numero = ejemplo.substring(ejemplo.indexOf(' ') + 1);
            if (!RESTRICCION_DE_LA_BASE.matcher(numero).matches()) {
                regiones.add(ejemplo.substring(0, ejemplo.indexOf(' ')));
            }
        }

        assertThat(regiones).containsExactlyInAnyOrderElementsOf(REGIONES_FUERA_DE_LA_BASE);
    }
}
