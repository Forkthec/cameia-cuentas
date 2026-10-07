package tech.cameia.cuentas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Comprueba que el contexto completo arranca, con las migraciones aplicadas.
 *
 * <p>Usa PostgreSQL en Testcontainers, como las demás pruebas de integración: así no depende de
 * que haya una base en {@code localhost:5432} ni de cuál sea, que en un equipo con varios
 * servicios puede ser la de otro.</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CuentasApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Test
	void contextLoads() {
	}

}
