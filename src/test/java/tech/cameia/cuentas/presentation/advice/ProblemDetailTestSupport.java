package tech.cameia.cuentas.presentation.advice;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;

import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import tech.cameia.cuentas.domain.model.CurrentPolicy;
import tech.cameia.cuentas.presentation.dto.CurrentPolicyVersionValidator;

/**
 * Da acceso al manejador de errores desde las pruebas de los controladores.
 *
 * <p>{@code BusinessExceptionHandler} es de alcance de paquete a propósito: nadie fuera de
 * {@code presentation.advice} debería instanciarlo. Esta clase vive en ese mismo paquete,
 * pero en el código de pruebas, y solo sirve para armar el {@code MockMvc} con el mismo
 * manejador que usa la aplicación. Sin ella, las pruebas comprobarían el formato de error
 * de un manejador distinto al real.</p>
 */
public final class ProblemDetailTestSupport {

    private ProblemDetailTestSupport() {
    }

    /**
     * Crea el manejador de errores real.
     *
     * @return manejador que traduce las excepciones a {@code application/problem+json}
     */
    public static Object manejadorDeErrores() {
        return new BusinessExceptionHandler();
    }

    /**
     * Crea un validador de Bean Validation que sabe construir la restricción de la versión vigente.
     *
     * <p>{@code standaloneSetup} no tiene el contexto de Spring que entrega la política vigente al
     * validador, así que esta fábrica la entrega a mano; el resto de validadores los construye Hibernate
     * Validator como siempre.</p>
     *
     * @param version versión vigente de la política que aceptará el validador
     * @return validador ya inicializado para usar en {@code setValidator}
     */
    public static LocalValidatorFactoryBean validadorConPolitica(String version) {
        CurrentPolicy politica = new CurrentPolicy(version);
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.setConstraintValidatorFactory(new ConstraintValidatorFactory() {
            @Override
            public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> clase) {
                if (clase == CurrentPolicyVersionValidator.class) {
                    return clase.cast(new CurrentPolicyVersionValidator(politica));
                }
                try {
                    return clase.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException("No se pudo crear el validador " + clase.getName(), error);
                }
            }

            @Override
            public void releaseInstance(ConstraintValidator<?, ?> instancia) {
                // Los validadores de las pruebas no guardan recursos que liberar.
            }
        });
        validador.afterPropertiesSet();
        return validador;
    }
}
