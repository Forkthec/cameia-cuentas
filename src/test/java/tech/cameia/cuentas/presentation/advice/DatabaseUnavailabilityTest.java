package tech.cameia.cuentas.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.stream.Stream;

import org.hibernate.TransactionException;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;

/** Reconocimiento de la indisponibilidad de la base de datos en una cadena de excepciones. */
class DatabaseUnavailabilityTest {

    @Test
    @DisplayName("Reconoce el pool de conexiones agotado")
    void matches_shouldBeTrue_whenPoolIsExhausted() {
        SQLTransientConnectionException pool = new SQLTransientConnectionException("pool");
        Exception error = new CannotCreateTransactionException("x", new JDBCConnectionException("x", pool));

        assertThat(DatabaseUnavailability.matches(error)).isTrue();
    }

    @Test
    @DisplayName("Reconoce un apagado del servidor aunque la conexión cerrada esté entre las suprimidas")
    void matches_shouldBeTrue_whenSqlStateIsAdminShutdown() {
        DataAccessResourceFailureException error = new DataAccessResourceFailureException("x",
                new SQLException("FATAL", "57P01"));
        error.addSuppressed(new SQLException("Connection is closed"));

        assertThat(DatabaseUnavailability.matches(error)).isTrue();
    }

    @Test
    @DisplayName("Reconoce un fallo al deshacer la transacción sobre una conexión cerrada, sin SQLState")
    void matches_shouldBeTrue_whenRollbackFailsOnClosedConnection() {
        Exception error = new JpaSystemException(
                new TransactionException("Unable to rollback", new SQLException("Connection is closed")));

        assertThat(DatabaseUnavailability.matches(error)).isTrue();
    }

    @Test
    @DisplayName("Reconoce la causa que solo aparece entre las excepciones suprimidas")
    void matches_shouldBeTrue_whenOnlySuppressedIsConnectionError() {
        RuntimeException error = new RuntimeException("x");
        error.addSuppressed(new SQLException("x", "08006"));

        assertThat(DatabaseUnavailability.matches(error)).isTrue();
    }

    @ParameterizedTest(name = "SQLState {0}")
    @ValueSource(strings = {"08001", "08006", "57P01", "57P02", "57P03", "53300"})
    @DisplayName("Reconoce los SQLState de conexión, apagado y exceso de conexiones")
    void matches_shouldBeTrue_whenSqlStateIsConnectionClass(String sqlState) {
        assertThat(DatabaseUnavailability.matches(new SQLException("x", sqlState))).isTrue();
    }

    @Test
    @DisplayName("Reconoce una consulta que pasó su tiempo máximo")
    void matches_shouldBeTrue_whenQueryTimesOut() {
        Exception error = new QueryTimeoutException("x", new SQLException("canceling", "57014"));

        assertThat(DatabaseUnavailability.matches(error)).isTrue();
    }

    static Stream<Arguments> notAvailabilityProblems() {
        return Stream.of(
                Arguments.of("violación de no nulo (23502)",
                        new DataIntegrityViolationException("x", new SQLException("x", "23502"))),
                Arguments.of("SQL inválido (42601)",
                        new BadSqlGrammarException("x", "select", new SQLException("x", "42601"))),
                Arguments.of("espera de bloqueo agotada (55P03)",
                        new CannotAcquireLockException("x", new SQLException("x", "55P03"))),
                Arguments.of("estado ilegal", new IllegalStateException("x")),
                Arguments.of("excepción sin causa", new RuntimeException()),
                Arguments.of("división por cero (22012)", new SQLException("x", "22012")),
                Arguments.of("transacción fallida por violación de unicidad al confirmar (23505)",
                        new TransactionException("Unable to commit", new SQLException("x", "23505"))),
                Arguments.of("transacción fallida por SQL inválido (42601)",
                        new JpaSystemException(new RuntimeException("x",
                                new TransactionException("Unable to commit", new SQLException("x", "42601"))))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notAvailabilityProblems")
    @DisplayName("No confunde un fallo del dato o de la consulta con una base caída")
    void matches_shouldBeFalse_whenNotAvailabilityProblem(String caso, Throwable error) {
        assertThat(DatabaseUnavailability.matches(error)).isFalse();
    }

    @Test
    @DisplayName("Una excepción nula no es indisponibilidad")
    void matches_shouldBeFalse_whenNull() {
        assertThat(DatabaseUnavailability.matches(null)).isFalse();
    }

    @Test
    @DisplayName("Una cadena de causas con ciclo termina")
    void matches_shouldTerminate_whenCauseCycle() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);

        assertThat(DatabaseUnavailability.matches(a)).isFalse();
    }

    @Test
    @DisplayName("No visita más de 64 excepciones")
    void matches_shouldStopAtLimit_whenChainIsTooDeep() {
        assertThat(DatabaseUnavailability.matches(chainOf(100))).isFalse();
    }

    @Test
    @DisplayName("Visita la excepción número 64")
    void matches_shouldFind_whenOutageIsAtNode64() {
        assertThat(DatabaseUnavailability.matches(chainOf(63))).isTrue();
    }

    @Test
    @DisplayName("Devuelve el primer SQLState del árbol")
    void sqlState_shouldReturnFirstState_whenPresent() {
        assertThat(DatabaseUnavailability.sqlState(new RuntimeException("x", new SQLException("x", "57P01"))))
                .isEqualTo("57P01");
    }

    @Test
    @DisplayName("Devuelve «desconocido» cuando ninguna excepción trae SQLState")
    void sqlState_shouldReturnUnknown_whenAbsent() {
        assertThat(DatabaseUnavailability.sqlState(new RuntimeException())).isEqualTo("desconocido");
        assertThat(DatabaseUnavailability.sqlState(null)).isEqualTo("desconocido");
    }

    /** Cadena de {@code depth} excepciones con un corte de conexión (08001) como último nodo. */
    private static Throwable chainOf(int depth) {
        Throwable error = new SQLException("x", "08001");
        for (int i = 0; i < depth; i++) {
            error = new RuntimeException("n" + i, error);
        }
        return error;
    }
}
