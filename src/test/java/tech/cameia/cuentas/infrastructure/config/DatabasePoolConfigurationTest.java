package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Comprueba la configuración efectiva del pool de conexiones. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DatabasePoolConfigurationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("El pool espera como máximo 5 segundos por una conexión")
    void connectionTimeout_shouldBeFiveSeconds() {
        assertThat(((HikariDataSource) dataSource).getConnectionTimeout()).isEqualTo(5000L);
    }
}
