package tech.cameia.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.PhoneNumberUtil.ValidationResult;
import com.google.i18n.phonenumbers.Phonenumber;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.InvalidPhoneNumberException;

/**
 * Comprueba la relación entre la validación por país y la restricción
 * {@code ck_cuenta_telefono_e164} de la base ({@code ^\+[1-9][0-9]{5,14}$}).
 *
 * <p>Lo que no puede pasar es que un número aceptado no quepa en la base: el registro pasaría la
 * validación y fallaría al guardar con un error interno. Ni que un número válido se rechace por
 * una longitud que la base no admite, porque el formulario web lo acepta con la misma
 * librería.</p>
 */
class PhoneNumberDatabaseCompatibilityTest {

    /** La misma expresión de la restricción de la base. */
    private static final Pattern RESTRICCION_DE_LA_BASE = Pattern.compile("^\\+[1-9][0-9]{5,14}$");

    /** Máximo de dígitos de un número E.164, con el indicativo. */
    private static final int MAXIMO_E164 = 15;

    private static final List<String> EJEMPLOS = new ArrayList<>();

    @BeforeAll
    static void reunirLosEjemplos() {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        for (String region : util.getSupportedRegions()) {
            for (PhoneNumberType tipo : List.of(PhoneNumberType.MOBILE, PhoneNumberType.FIXED_LINE,
                    PhoneNumberType.FIXED_LINE_OR_MOBILE)) {
                Phonenumber.PhoneNumber ejemplo = util.getExampleNumberForType(region, tipo);
                if (ejemplo != null) {
                    EJEMPLOS.add(util.format(ejemplo, PhoneNumberFormat.E164));
                }
            }
        }
    }

    @Test
    void todoNumeroDeEjemploSeAceptaYCabeEnLaBase() {
        List<String> rechazados = new ArrayList<>();
        List<String> aceptadosQueNoCaben = new ArrayList<>();
        for (String numero : EJEMPLOS) {
            try {
                PhoneNumber.fromInput(numero);
                if (!RESTRICCION_DE_LA_BASE.matcher(numero).matches()) {
                    aceptadosQueNoCaben.add(numero);
                }
            } catch (InvalidPhoneNumberException rechazado) {
                rechazados.add(numero);
            }
        }

        assertThat(EJEMPLOS).hasSizeGreaterThan(400);
        assertThat(rechazados).isEmpty();
        assertThat(aceptadosQueNoCaben).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"+2908999", "+6903101", "+6837012"})
    void losNumerosValidosDeSieteDigitosSeAceptan(String numero) {
        // Tristan da Cunha, Tokelau y Niue: son los que la restricción anterior, de 8 dígitos
        // como mínimo, dejaba fuera.
        assertThat(PhoneNumber.fromInput(numero).value()).isEqualTo(numero);
        assertThat(RESTRICCION_DE_LA_BASE.matcher(numero).matches()).isTrue();
    }

    @Test
    void elNumeroPosibleMasCortoDeCualquierPaisCabeEnLaBase() {
        // Recorre las longitudes posibles de cada país, no solo los ejemplos: una versión nueva
        // de la librería con un número más corto haría fallar esta prueba antes que el registro.
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        int masCorto = Integer.MAX_VALUE;
        for (String region : util.getSupportedRegions()) {
            int indicativo = util.getCountryCodeForRegion(region);
            for (int largo = 1; largo <= MAXIMO_E164; largo++) {
                Phonenumber.PhoneNumber numero = new Phonenumber.PhoneNumber().setCountryCode(indicativo)
                        .setNationalNumber(Long.parseLong("2".repeat(largo)));
                if (util.isPossibleNumberWithReason(numero) == ValidationResult.IS_POSSIBLE) {
                    masCorto = Math.min(masCorto, String.valueOf(indicativo).length() + largo);
                }
            }
        }

        assertThat(masCorto).isEqualTo(6);
        assertThat(RESTRICCION_DE_LA_BASE.matcher("+" + "1".repeat(masCorto)).matches()).isTrue();
        assertThat(RESTRICCION_DE_LA_BASE.matcher("+" + "1".repeat(masCorto - 1)).matches()).isFalse();
    }
}
