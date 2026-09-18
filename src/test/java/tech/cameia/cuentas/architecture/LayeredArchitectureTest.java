package tech.cameia.cuentas.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifica la regla de dependencias entre capas descrita en CLAUDE.md ("Estructura de
 * carpetas"): `domain` no importa nada de `presentation`, `application` ni
 * `infrastructure`; `application` depende de `domain` a través de puertos y nunca
 * directamente de `infrastructure` ni de `presentation`.
 *
 * <p>Esta prueba corre sobre las clases compiladas del microservicio, así que solo
 * detecta violaciones en código ya existente; no reemplaza la revisión de diseño al
 * introducir una clase nueva.
 */
class LayeredArchitectureTest {

    private static final String BASE_PACKAGE = "tech.cameia.cuentas";

    private static JavaClasses classes;

    @BeforeAll
    static void importarClases() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    @Test
    void elDominioNoDependeDeLasCapasSuperiores() {
        // allowEmptyShould(true): domain aún no tiene clases (carpetas con .gitkeep).
        // Sin este permiso ArchUnit falla la regla por no evaluar ninguna clase; en
        // cuanto exista código en domain, esta regla lo empieza a validar igual.
        ArchRule regla = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        BASE_PACKAGE + ".presentation..",
                        BASE_PACKAGE + ".application..",
                        BASE_PACKAGE + ".infrastructure..")
                .allowEmptyShould(true);

        regla.check(classes);
    }

    @Test
    void elDominioNoConoceLaTecnologiaQueLoRodea() {
        // El dominio define puertos; quien habla con Firebase, con JPA o con Spring es la
        // infraestructura. Si esta regla se rompe, las reglas de negocio dejan de poder
        // probarse sin levantar medio sistema.
        ArchRule regla = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.google.firebase..",
                        "jakarta.persistence..",
                        "org.springframework..")
                .allowEmptyShould(true);

        regla.check(classes);
    }

    @Test
    void laAplicacionNoDependeDeInfraestructuraNiDePresentacion() {
        // allowEmptyShould(true): application aún no tiene clases (carpetas con .gitkeep).
        ArchRule regla = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        BASE_PACKAGE + ".infrastructure..",
                        BASE_PACKAGE + ".presentation..")
                .allowEmptyShould(true);

        regla.check(classes);
    }
}
