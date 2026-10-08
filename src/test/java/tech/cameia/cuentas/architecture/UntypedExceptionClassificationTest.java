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

import org.junit.jupiter.api.DisplayName;
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
    private static final String DEFENSIVE_INVARIANT = "invariante defensiva";

    /** Ocurre al arrancar: la aplicación no arranca y no hay petición que responder. */
    private static final String STARTUP = "configuración al arrancar";

    /** Lo que nadie puede prever: un rechazo de Firebase que no es indisponibilidad ni correo inválido. */
    private static final String UNEXPECTED_FAILURE = "fallo imprevisto";

    /** Previsible, con su corrección asignada a otra tarea y anotada en docs/errores.md. */
    private static final String PENDING_CM_179 = "pendiente de CM-179 (activación)";

    private static final String MODEL_PACKAGE = "tech.cameia.cuentas.domain.model.";
    private static final String CONFIGURATION_PACKAGE = "tech.cameia.cuentas.infrastructure.config.";
    private static final String CLIENT_PACKAGE = "tech.cameia.cuentas.infrastructure.client.";

    private static final Map<String, String> CLASSIFICATION = Map.ofEntries(
            Map.entry(MODEL_PACKAGE + "Account#activate", PENDING_CM_179),
            Map.entry(MODEL_PACKAGE + "Account#exigirTexto", DEFENSIVE_INVARIANT),
            Map.entry(MODEL_PACKAGE + "BirthDate#<init>", DEFENSIVE_INVARIANT),
            Map.entry(MODEL_PACKAGE + "DirectoryUser#<init>", DEFENSIVE_INVARIANT),
            Map.entry(MODEL_PACKAGE + "EmailAddress#<init>", DEFENSIVE_INVARIANT),
            Map.entry(MODEL_PACKAGE + "PersonName#<init>", DEFENSIVE_INVARIANT),
            Map.entry(MODEL_PACKAGE + "PhoneNumber#<init>", DEFENSIVE_INVARIANT),
            Map.entry(MODEL_PACKAGE + "PhoneNumber#fromInput", DEFENSIVE_INVARIANT),
            Map.entry(MODEL_PACKAGE + "RawPassword#<init>", DEFENSIVE_INVARIANT),
            Map.entry(MODEL_PACKAGE + "SingleLineText#<init>", DEFENSIVE_INVARIANT),
            Map.entry("tech.cameia.cuentas.presentation.dto.RegisterUserRequest#toCommand", DEFENSIVE_INVARIANT),
            Map.entry(CONFIGURATION_PACKAGE + "CommonPasswordsLoader#load", STARTUP),
            Map.entry(CONFIGURATION_PACKAGE + "CommonPasswordsLoader#validate", STARTUP),
            Map.entry(CONFIGURATION_PACKAGE + "FirebaseConfiguration#firebaseApp", STARTUP),
            Map.entry(CONFIGURATION_PACKAGE + "FirebaseConfiguration#rejectEmulatorInDeployment", STARTUP),
            Map.entry(CLIENT_PACKAGE + "FirebaseUserDirectoryAdapter#unavailableOrRejection", UNEXPECTED_FAILURE),
            Map.entry(CLIENT_PACKAGE + "FirebaseUserDirectoryAdapter#isEmailVerified", PENDING_CM_179));

    @Test
    @DisplayName("Todo método que lanza una excepción sin código está clasificado con su motivo")
    void untypedExceptions_shouldBeClassified_whenAnyMethodThrowsOne() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("tech.cameia.cuentas");

        Set<String> origins = new TreeSet<>();
        classes.forEach(javaClass -> {
            javaClass.getConstructorCallsFromSelf().stream()
                    .filter(UntypedExceptionClassificationTest::constructsUntypedException)
                    .forEach(call -> origins.add(origin(call)));
            javaClass.getMethodCallsFromSelf().stream()
                    .filter(UntypedExceptionClassificationTest::throwsUntypedException)
                    .forEach(call -> origins.add(origin(call)));
        });

        assertThat(origins)
                .as("Cada método que lanza una RuntimeException que no es de negocio debe estar en "
                        + "CLASSIFICATION; si el fallo es previsible, necesita su excepción de negocio y su código")
                .isEqualTo(new TreeSet<>(CLASSIFICATION.keySet()));
    }

    private static boolean constructsUntypedException(JavaConstructorCall call) {
        return call.getTargetOwner().isAssignableTo(RuntimeException.class)
                && !call.getTargetOwner().isAssignableTo(BusinessException.class)
                && !isSuperCall(call);
    }

    /** {@code super(...)} en el constructor de una excepción es herencia, no un lanzamiento. */
    private static boolean isSuperCall(JavaConstructorCall call) {
        return call.getOrigin().isConstructor()
                && call.getOriginOwner().getRawSuperclass()
                        .map(parent -> parent.equals(call.getTargetOwner()))
                        .orElse(false);
    }

    private static boolean throwsUntypedException(JavaMethodCall call) {
        String owner = call.getTargetOwner().getName();
        String method = call.getName();
        // requireNonNull de un solo argumento lo inserta javac en cada referencia de método
        // (mapeador::toDomain); el que se escribe a mano lleva mensaje.
        return (owner.equals(Objects.class.getName()) && method.startsWith("requireNonNull")
                        && call.getTarget().getRawParameterTypes().size() > 1)
                || (owner.equals(Optional.class.getName()) && method.equals("orElseThrow")
                        && call.getTarget().getRawParameterTypes().isEmpty());
    }

    /** Clase y método de origen; una lambda cuenta como el método que la contiene. */
    private static String origin(JavaAccess<?> call) {
        JavaCodeUnit unit = call.getOrigin();
        String method = unit.getName();
        if (method.startsWith("lambda$")) {
            method = method.substring("lambda$".length(), method.lastIndexOf('$'));
        }
        return call.getOriginOwner().getName() + "#" + method;
    }
}
