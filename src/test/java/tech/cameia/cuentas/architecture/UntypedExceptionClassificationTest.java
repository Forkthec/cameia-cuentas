package tech.cameia.cuentas.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.Test;

import tech.cameia.cuentas.domain.exception.BusinessException;

/**
 * Exige que todo método que puede lanzar una excepción sin código esté clasificado aquí con su
 * motivo.
 *
 * <p>Una excepción sin código es cualquier {@link RuntimeException} que no es de negocio
 * ({@code IllegalStateException}, {@code NullPointerException}, {@code UncheckedIOException}…),
 * también la que lanzan {@link Objects#requireNonNull(Object, String)} y {@link Optional#orElseThrow()}. Si
 * una llega a una petición, el manejador de respaldo responde {@code 500 INTERNAL_ERROR}. Eso solo
 * es correcto para lo que nadie puede prever o lo que ninguna petición alcanza; un fallo previsible
 * lleva su excepción de negocio y su código (REQ-RV-07 de CM-36). Un método nuevo que lance una de
 * ellas hace fallar esta prueba hasta que alguien decida su motivo.</p>
 */
class UntypedExceptionClassificationTest {

    /** Inalcanzable por la API: el contrato HTTP rechaza el dato antes de llegar aquí. */
    private static final String INVARIANTE = "invariante defensiva";

    /** Ocurre al arrancar: la aplicación no arranca y no hay petición que responder. */
    private static final String ARRANQUE = "configuración al arrancar";

    /** Lo que nadie puede prever: un rechazo de Firebase que no es indisponibilidad ni correo inválido. */
    private static final String DEFECTO = "fallo imprevisto";

    /** Previsible, con su corrección asignada a otra tarea y anotada en docs/errores.md. */
    private static final String PENDIENTE_CM_179 = "pendiente de CM-179 (activación)";

    private static final String MODELO = "tech.cameia.cuentas.domain.model.";
    private static final String CONFIG = "tech.cameia.cuentas.infrastructure.config.";
    private static final String CLIENTE = "tech.cameia.cuentas.infrastructure.client.";

    private static final Map<String, String> CLASIFICACION = Map.ofEntries(
            Map.entry(MODELO + "Account#activate", PENDIENTE_CM_179),
            Map.entry(MODELO + "Account#exigirTexto", INVARIANTE),
            Map.entry(MODELO + "BirthDate#<init>", INVARIANTE),
            Map.entry(MODELO + "EmailAddress#<init>", INVARIANTE),
            Map.entry(MODELO + "PersonName#<init>", INVARIANTE),
            Map.entry(MODELO + "PhoneNumber#<init>", INVARIANTE),
            Map.entry(MODELO + "PhoneNumber#fromInput", INVARIANTE),
            Map.entry(MODELO + "RawPassword#<init>", INVARIANTE),
            Map.entry(MODELO + "SingleLineText#<init>", INVARIANTE),
            Map.entry("tech.cameia.cuentas.presentation.dto.RegisterUserRequest#toCommand", INVARIANTE),
            Map.entry(CONFIG + "CommonPasswordsLoader#load", ARRANQUE),
            Map.entry(CONFIG + "CommonPasswordsLoader#validate", ARRANQUE),
            Map.entry(CONFIG + "FirebaseConfiguration#firebaseApp", ARRANQUE),
            Map.entry(CONFIG + "FirebaseConfiguration#rejectEmulatorInDeployment", ARRANQUE),
            Map.entry(CLIENTE + "FirebaseUserDirectoryAdapter#indisponibleOEnRechazo", DEFECTO),
            Map.entry(CLIENTE + "FirebaseUserDirectoryAdapter#isEmailVerified", PENDIENTE_CM_179));

    @Test
    void todoMetodoQueLanzaUnaExcepcionSinCodigoEstaClasificado() {
        JavaClasses clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("tech.cameia.cuentas");

        Set<String> origenes = new TreeSet<>();
        clases.forEach(clase -> {
            clase.getConstructorCallsFromSelf().stream()
                    .filter(UntypedExceptionClassificationTest::construyeExcepcionSinCodigo)
                    .forEach(llamada -> origenes.add(origen(llamada)));
            clase.getMethodCallsFromSelf().stream()
                    .filter(UntypedExceptionClassificationTest::lanzaExcepcionSinCodigo)
                    .forEach(llamada -> origenes.add(origen(llamada)));
        });

        assertThat(origenes)
                .as("Cada método que lanza una RuntimeException que no es de negocio debe estar en "
                        + "CLASIFICACION; si el fallo es previsible, necesita su excepción de negocio y su código")
                .isEqualTo(new TreeSet<>(CLASIFICACION.keySet()));
    }

    private static boolean construyeExcepcionSinCodigo(JavaConstructorCall llamada) {
        return llamada.getTargetOwner().isAssignableTo(RuntimeException.class)
                && !llamada.getTargetOwner().isAssignableTo(BusinessException.class)
                && !esLlamadaASuper(llamada);
    }

    /** {@code super(...)} en el constructor de una excepción es herencia, no un lanzamiento. */
    private static boolean esLlamadaASuper(JavaConstructorCall llamada) {
        return llamada.getOrigin().isConstructor()
                && llamada.getOriginOwner().getRawSuperclass()
                        .map(padre -> padre.equals(llamada.getTargetOwner()))
                        .orElse(false);
    }

    private static boolean lanzaExcepcionSinCodigo(JavaMethodCall llamada) {
        String duenio = llamada.getTargetOwner().getName();
        String metodo = llamada.getName();
        // requireNonNull de un solo argumento lo inserta javac en cada referencia de método
        // (mapeador::toDomain); el que se escribe a mano lleva mensaje.
        return (duenio.equals(Objects.class.getName()) && metodo.startsWith("requireNonNull")
                        && llamada.getTarget().getRawParameterTypes().size() > 1)
                || (duenio.equals(Optional.class.getName()) && metodo.equals("orElseThrow")
                        && llamada.getTarget().getRawParameterTypes().isEmpty());
    }

    /** Clase y método de origen; una lambda cuenta como el método que la contiene. */
    private static String origen(JavaAccess<?> llamada) {
        JavaCodeUnit unidad = llamada.getOrigin();
        String metodo = unidad.getName();
        if (metodo.startsWith("lambda$")) {
            metodo = metodo.substring("lambda$".length(), metodo.lastIndexOf('$'));
        }
        return llamada.getOriginOwner().getName() + "#" + metodo;
    }
}
