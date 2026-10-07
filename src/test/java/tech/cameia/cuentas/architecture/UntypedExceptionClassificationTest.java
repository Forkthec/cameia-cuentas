package tech.cameia.cuentas.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.Test;

/**
 * Exige que toda clase que lanza una excepción sin código ({@link IllegalStateException} o
 * {@link IllegalArgumentException}) esté clasificada aquí con su motivo.
 *
 * <p>Esas excepciones no son de negocio: si una llega a una petición, el manejador de respaldo
 * responde {@code 500 INTERNAL_ERROR}. Eso solo es correcto para lo que nadie puede prever (un
 * defecto) o lo que ninguna petición alcanza; un fallo previsible lleva su excepción de negocio
 * y su código (REQ-RV-07 de CM-36). Una clase nueva que lance una de ellas hace fallar esta
 * prueba hasta que alguien decida en cuál de esos casos está.</p>
 */
class UntypedExceptionClassificationTest {

    /** Inalcanzable por la API: el contrato HTTP rechaza el dato antes de construir el objeto. */
    private static final String INVARIANTE = "invariante defensiva";

    /** Ocurre al arrancar: la aplicación no arranca y no hay petición que responder. */
    private static final String ARRANQUE = "configuración al arrancar";

    /** Lo que nadie puede prever: un rechazo de Firebase que no es indisponibilidad ni dato inválido. */
    private static final String DEFECTO = "fallo imprevisto";

    /** Previsible, con su corrección asignada a otra tarea y anotada en docs/errores.md. */
    private static final String PENDIENTE_CM_179 = "pendiente de CM-179 (activación)";

    private static final Map<String, String> CLASIFICACION = Map.ofEntries(
            Map.entry("tech.cameia.cuentas.domain.model.Account", INVARIANTE + " y " + PENDIENTE_CM_179),
            Map.entry("tech.cameia.cuentas.domain.model.BirthDate", INVARIANTE),
            Map.entry("tech.cameia.cuentas.domain.model.EmailAddress", INVARIANTE),
            Map.entry("tech.cameia.cuentas.domain.model.PersonName", INVARIANTE),
            Map.entry("tech.cameia.cuentas.domain.model.PhoneNumber", INVARIANTE),
            Map.entry("tech.cameia.cuentas.domain.model.RawPassword", INVARIANTE),
            Map.entry("tech.cameia.cuentas.infrastructure.config.CommonPasswordsLoader", ARRANQUE),
            Map.entry("tech.cameia.cuentas.infrastructure.config.FirebaseConfiguration", ARRANQUE),
            Map.entry("tech.cameia.cuentas.infrastructure.client.FirebaseUserDirectoryAdapter",
                    DEFECTO + " y " + PENDIENTE_CM_179));

    @Test
    void todaClaseQueLanzaUnaExcepcionSinCodigoEstaClasificada() {
        JavaClasses clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("tech.cameia.cuentas");

        Set<String> origenes = new TreeSet<>();
        clases.forEach(clase -> clase.getConstructorCallsFromSelf().stream()
                .filter(UntypedExceptionClassificationTest::construyeExcepcionSinCodigo)
                .forEach(llamada -> origenes.add(llamada.getOriginOwner().getName())));

        assertThat(origenes)
                .as("Cada clase que lanza IllegalStateException o IllegalArgumentException debe estar en "
                        + "CLASIFICACION; si el fallo es previsible, necesita su excepción de negocio y su código")
                .isEqualTo(CLASIFICACION.keySet().stream().collect(TreeSet::new, Set::add, Set::addAll));
    }

    private static boolean construyeExcepcionSinCodigo(JavaConstructorCall llamada) {
        String tipo = llamada.getTargetOwner().getName();
        return tipo.equals(IllegalStateException.class.getName())
                || tipo.equals(IllegalArgumentException.class.getName());
    }
}
