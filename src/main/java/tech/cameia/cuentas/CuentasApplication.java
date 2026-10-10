package tech.cameia.cuentas;

import java.util.function.IntConsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

import tech.cameia.cuentas.infrastructure.config.AccountEventsRelayJobRunner;

@SpringBootApplication
public class CuentasApplication {

	/**
	 * Arranca la aplicación y, si es una tarea de una sola ejecución, termina el proceso con su código de salida.
	 *
	 * @param args argumentos de la línea de comandos
	 */
	public static void main(String[] args) {
		exitWhenOneShotJob(SpringApplication.run(CuentasApplication.class, args), System::exit);
	}

	/**
	 * Cierra el contexto y termina el proceso cuando está activo el perfil de una tarea de una sola ejecución.
	 *
	 * <p>Los hilos de RabbitMQ mantendrían viva la JVM después de la tarea; el código de salida es el del contexto, así que una
	 * excepción al arrancar deja un código distinto de 0 y el Job se reintenta.</p>
	 *
	 * @param context contexto ya arrancado
	 * @param exit acción que termina el proceso con el código recibido
	 */
	static void exitWhenOneShotJob(ConfigurableApplicationContext context, IntConsumer exit) {
		if (context.getEnvironment().acceptsProfiles(Profiles.of(AccountEventsRelayJobRunner.PROFILE))) {
			exit.accept(SpringApplication.exit(context));
		}
	}

}
