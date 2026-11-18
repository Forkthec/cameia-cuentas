package tech.cameia.cuentas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integración del esquema descrito en
 * {@code specs/CM-14-RegistroUsuario/spec.md} (REQ-CU-15 y REQ-CU-16).
 *
 * <p>Levanta PostgreSQL 16 real en un contenedor con puerto aleatorio. El arranque del
 * contexto ya demuestra dos cosas: que Flyway aplica la migración sobre una base vacía y
 * que Hibernate valida el mapeo contra el esquema resultante, porque
 * {@code ddl-auto=validate} aborta el arranque si no coinciden.</p>
 *
 * <p>El resto de las pruebas comprueba las restricciones que viven en la base y no en el
 * dominio: son la red de seguridad para cuando el error esté en el código.</p>
 *
 * <p>Se omite cuando no hay Docker disponible, para que {@code ./mvnw.cmd test} siga
 * siendo ejecutable en una máquina sin contenedores. La integración continua sí tiene
 * Docker y la ejecuta.</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CuentaSchemaMigrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * Comprueba que la migración crea la tabla dentro del esquema {@code microcuentas} y
	 * no en {@code public}.
	 */
	@Test
	void migracionCreaLaTablaCuentaEnSuEsquema() {
		Integer tablas = jdbcTemplate.queryForObject("""
				SELECT count(*) FROM information_schema.tables
				 WHERE table_schema = 'microcuentas' AND table_name = 'cuenta'
				""", Integer.class);

		assertThat(tablas).isEqualTo(1);
	}

	/**
	 * Comprueba que existe el índice parcial que usa la purga de cuentas sin verificar.
	 */
	@Test
	void indicePendienteExisteYSoloCubreLasCuentasSinVerificar() {
		List<String> definiciones = jdbcTemplate.queryForList("""
				SELECT indexdef FROM pg_indexes
				 WHERE schemaname = 'microcuentas' AND indexname = 'ix_cuenta_pendiente_fecha_creacion'
				""", String.class);

		assertThat(definiciones).hasSize(1);
		assertThat(definiciones.get(0)).contains("(fecha_creacion)").contains("WHERE").contains("PENDING_VERIFICATION");
	}

	/**
	 * Verifica que una fila insertada sin estado explícito queda pendiente de verificar y
	 * nunca activa.
	 */
	@Test
	void cuentaNuevaNaceEnPendingVerification() {
		insertarCuenta("uid-por-defecto", null);

		String estado = jdbcTemplate.queryForObject(
				"SELECT estado FROM microcuentas.cuenta WHERE firebase_uid = ?",
				String.class, "uid-por-defecto");

		assertThat(estado).isEqualTo("PENDING_VERIFICATION");
	}

	/**
	 * Verifica que la base rechaza un estado que no pertenece a la máquina de estados.
	 */
	@Test
	void estadoFueraDeLaListaEsRechazado() {
		assertThatThrownBy(() -> insertarCuenta("uid-estado-invalido", "VERIFICADO"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	/**
	 * Verifica que un mismo usuario de Firebase no puede tener dos cuentas locales.
	 */
	@Test
	void firebaseUidDuplicadoEsRechazado() {
		insertarCuenta("uid-repetido", null);

		assertThatThrownBy(() -> insertarCuenta("uid-repetido", null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	/**
	 * Comprueba que la migración agrega las dos columnas de la aceptación de la política,
	 * obligatorias y con el tamaño que valida el dominio, y su restricción de no vacío.
	 */
	@Test
	void schema_shouldHavePolicyColumns_whenMigrated() {
		List<java.util.Map<String, Object>> columnas = jdbcTemplate.queryForList("""
				SELECT column_name, data_type, character_maximum_length, is_nullable
				  FROM information_schema.columns
				 WHERE table_schema = 'microcuentas' AND table_name = 'cuenta'
				   AND column_name IN ('politica_version', 'politica_aceptada_en')
				 ORDER BY column_name
				""");

		assertThat(columnas).hasSize(2);
		assertThat(columnas.get(0)).containsEntry("column_name", "politica_aceptada_en")
				.containsEntry("data_type", "timestamp with time zone")
				.containsEntry("is_nullable", "NO");
		assertThat(columnas.get(1)).containsEntry("column_name", "politica_version")
				.containsEntry("data_type", "character varying")
				.containsEntry("character_maximum_length", 20)
				.containsEntry("is_nullable", "NO");
		Integer restricciones = jdbcTemplate.queryForObject("""
				SELECT count(*) FROM pg_constraint
				 WHERE conrelid = 'microcuentas.cuenta'::regclass
				   AND conname = 'ck_cuenta_politica_version_no_vacia'
				""", Integer.class);
		assertThat(restricciones).isEqualTo(1);
	}

	/**
	 * Verifica que la base rechaza una versión de política formada solo por espacios.
	 */
	@Test
	void insert_shouldFail_whenPolicyVersionIsBlank() {
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido,
				                                 politica_version, politica_aceptada_en)
				VALUES (gen_random_uuid(), 'uid-politica-vacia', 'Ana', 'Perez', '   ', now())
				"""))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_cuenta_politica_version_no_vacia");
	}

	/**
	 * Verifica que un teléfono sin formato E.164 no llega a guardarse.
	 */
	@Test
	void telefonoFueraDeE164EsRechazado() {
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido, telefono,
				                                 politica_version, politica_aceptada_en)
				VALUES (gen_random_uuid(), 'uid-telefono', 'Ana', 'Perez', '3001234567', '2026-11-v1', now())
				""")).isInstanceOf(DataIntegrityViolationException.class);
	}

	/**
	 * Verifica que la base solo admite los pronombres del enumerado del dominio.
	 */
	@Test
	void pronombreFueraDeLaListaEsRechazado() {
		assertThatThrownBy(() -> insertarCuentaConPronombre("uid-pronombre-invalido", "OTRO"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_cuenta_pronombres_valor");
	}

	/**
	 * Verifica que la restricción distingue mayúsculas: {@code he} no es un valor del enumerado.
	 */
	@Test
	void pronombreEnMinusculasEsRechazado() {
		assertThatThrownBy(() -> insertarCuentaConPronombre("uid-pronombre-minusculas", "he"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_cuenta_pronombres_valor");
	}

	/**
	 * Verifica que los tres pronombres y el nulo (cuenta anonimizada) se aceptan.
	 */
	@Test
	void losTresPronombresYElNuloSeAceptan() {
		insertarCuentaConPronombre("uid-he", "HE");
		insertarCuentaConPronombre("uid-she", "SHE");
		insertarCuentaConPronombre("uid-they", "THEY");
		insertarCuentaConPronombre("uid-sin-pronombre", null);

		Integer guardadas = jdbcTemplate.queryForObject("""
				SELECT count(*) FROM microcuentas.cuenta
				 WHERE firebase_uid IN ('uid-he', 'uid-she', 'uid-they', 'uid-sin-pronombre')
				""", Integer.class);
		assertThat(guardadas).isEqualTo(4);
	}

	/**
	 * Inserta una cuenta mínima con un pronombre dado.
	 *
	 * @param firebaseUid identificador del usuario en Firebase
	 * @param pronombre valor de la columna {@code pronombres}, o {@code null}
	 */
	private void insertarCuentaConPronombre(String firebaseUid, String pronombre) {
		jdbcTemplate.update("""
				INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido, pronombres,
				                                 politica_version, politica_aceptada_en)
				VALUES (gen_random_uuid(), ?, 'Ana', 'Perez', ?, '2026-11-v1', now())
				""", firebaseUid, pronombre);
	}

	/**
	 * Inserta una cuenta mínima.
	 *
	 * @param firebaseUid identificador del usuario en Firebase
	 * @param estado estado explícito, o {@code null} para dejar que actúe el valor por
	 *               defecto de la columna
	 */
	private void insertarCuenta(String firebaseUid, String estado) {
		if (estado == null) {
			jdbcTemplate.update("""
					INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido,
					                                 politica_version, politica_aceptada_en)
					VALUES (gen_random_uuid(), ?, 'Ana', 'Perez', '2026-11-v1', now())
					""", firebaseUid);
			return;
		}
		jdbcTemplate.update("""
				INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido, estado,
				                                 politica_version, politica_aceptada_en)
				VALUES (gen_random_uuid(), ?, 'Ana', 'Perez', ?, '2026-11-v1', now())
				""", firebaseUid, estado);
	}
}
