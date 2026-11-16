package com.sokhasaclinic.clinicbackend;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinic_test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "scheduling.enabled=false"
})
class ClinicBackendApplicationTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDataSourceConfig {
        @Bean
        @ConditionalOnMissingBean(DataSource.class)
        DataSource dataSource() throws Exception {
            DataSource mockDs = mock(DataSource.class);
            Connection mockConn = mock(Connection.class);
            DatabaseMetaData mockMeta = mock(DatabaseMetaData.class);
            when(mockDs.getConnection()).thenReturn(mockConn);
            when(mockConn.getMetaData()).thenReturn(mockMeta);
            when(mockMeta.getDatabaseProductName()).thenReturn("PostgreSQL");
            when(mockMeta.getDatabaseProductVersion()).thenReturn("17.0");
            when(mockMeta.getDriverName()).thenReturn("PostgreSQL JDBC Driver");
            when(mockMeta.getDriverVersion()).thenReturn("42.7.0");
            return mockDs;
        }
    }

    @Test
    void contextLoads() {
    }
}
