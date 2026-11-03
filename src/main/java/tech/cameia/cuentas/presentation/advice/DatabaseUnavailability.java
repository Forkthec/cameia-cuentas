package tech.cameia.cuentas.presentation.advice;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

import org.hibernate.TransactionException;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;

/**
 * Reconoce que un fallo se debe a que la base de datos no está disponible: no responde, la
 * conexión se cortó, el pool de conexiones se agotó o una consulta pasó su tiempo máximo.
 *
 * <p>Recorre la excepción, sus causas y sus excepciones suprimidas, porque el corte de una
 * conexión puede llegar como una excepción de transacción sin código de estado y con la causa
 * verdadera solo entre las suprimidas. No reconoce los fallos del dato o de la consulta
 * (violaciones de restricción, SQL inválido, espera de bloqueo agotada).</p>
 */
final class DatabaseUnavailability {

    /** Máximo de excepciones que se visitan: una cadena con ciclos no debe dejar el hilo en un bucle. */
    private static final int MAX_NODES = 64;

    /** SQLState que devuelve {@link #sqlState(Throwable)} cuando ninguna excepción trae uno. */
    private static final String UNKNOWN_STATE = "desconocido";

    /** Estados de apagado o arranque del servidor y de demasiadas conexiones. */
    private static final Set<String> OUTAGE_STATES = Set.of("57P01", "57P02", "57P03", "53300");

    private DatabaseUnavailability() {
    }

    /**
     * Indica si la excepción, alguna causa o alguna suprimida es una indisponibilidad de la base.
     *
     * @param error excepción que llegó al manejador; puede ser {@code null}
     * @return {@code true} si es una indisponibilidad de la base de datos
     */
    static boolean matches(Throwable error) {
        return firstMatch(error, node -> isOutage(node) ? Boolean.TRUE : null) != null;
    }

    /**
     * Primer SQLState que aparece en el árbol de excepciones, para diagnosticar en el registro.
     *
     * @param error excepción que llegó al manejador; puede ser {@code null}
     * @return el SQLState, o {@code "desconocido"} si ninguna excepción lo trae
     */
    static String sqlState(Throwable error) {
        String state = firstMatch(error,
                node -> node instanceof SQLException sql ? sql.getSQLState() : null);
        return state != null ? state : UNKNOWN_STATE;
    }

    /**
     * Recorre en anchura la excepción, sus causas y sus suprimidas, sin repetir nodos y con tope
     * de {@value #MAX_NODES}, y devuelve el primer resultado no nulo de {@code probe}.
     */
    private static <T> T firstMatch(Throwable error, Function<Throwable, T> probe) {
        if (error == null) {
            return null;
        }
        Deque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(error);
        while (!pending.isEmpty() && visited.size() < MAX_NODES) {
            Throwable node = pending.poll();
            if (!visited.add(node)) {
                continue;
            }
            T found = probe.apply(node);
            if (found != null) {
                return found;
            }
            if (node.getCause() != null) {
                pending.add(node.getCause());
            }
            Collections.addAll(pending, node.getSuppressed());
        }
        return null;
    }

    /**
     * Una sola excepción: ¿es un corte, un agotamiento o un tiempo agotado?
     *
     * <p>{@link TransactionException} es la forma en que Hibernate avisa de que no pudo abrir,
     * confirmar o deshacer la transacción. Casi siempre es la conexión, pero también puede
     * lanzarla cuando la base rechaza el dato al confirmar, así que no cuenta si alguna de sus
     * causas trae un SQLState que no es de conexión.</p>
     */
    private static boolean isOutage(Throwable error) {
        if (error instanceof TransactionException) {
            return !hasNonConnectionSqlState(error);
        }
        if (error instanceof DataAccessResourceFailureException
                || error instanceof TransientDataAccessResourceException
                || error instanceof QueryTimeoutException
                || error instanceof JDBCConnectionException
                || error instanceof SQLTransientConnectionException
                || error instanceof SQLNonTransientConnectionException
                || error instanceof SQLTimeoutException) {
            return true;
        }
        return error instanceof SQLException sql && isConnectionSqlState(sql.getSQLState());
    }

    /** ¿Alguna causa o suprimida de {@code error} trae un SQLState que no es de conexión (del dato o de la consulta)? */
    private static boolean hasNonConnectionSqlState(Throwable error) {
        return firstMatch(error, node -> node instanceof SQLException sql
                && sql.getSQLState() != null && !isConnectionSqlState(sql.getSQLState()) ? Boolean.TRUE : null) != null;
    }

    /** Error de conexión (clase {@code 08}), apagado o arranque del servidor, o demasiadas conexiones. */
    private static boolean isConnectionSqlState(String sqlState) {
        return sqlState != null && (sqlState.startsWith("08") || OUTAGE_STATES.contains(sqlState));
    }
}
