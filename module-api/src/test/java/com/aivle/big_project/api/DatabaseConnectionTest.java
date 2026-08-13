package com.aivle.big_project.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
public class DatabaseConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void testConnection() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection).isNotNull();
            System.out.println("==================================================");
            System.out.println("데이터베이스 연결 성공!");
            System.out.println("연결된 DB URL: " + connection.getMetaData().getURL());
            System.out.println("==================================================");
        }
    }
}
