package com.huynqb.laundrylocker.order.it;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationVersionTest {

    @Test
    void migrationVersionsAreUnique() throws Exception {
        URL resource = getClass().getClassLoader().getResource("db/migration");
        assertNotNull(resource, "order-service Flyway migrations must be available on the test classpath");

        Map<String, Long> counts;
        try (var files = Files.list(Path.of(resource.toURI()))) {
            counts = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("V\\d+__.*\\.sql"))
                    .collect(Collectors.groupingBy(name -> name.substring(0, name.indexOf("__")),
                            Collectors.counting()));
        }

        List<String> duplicates = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        assertTrue(duplicates.isEmpty(), () -> "Duplicate Flyway migration versions: " + duplicates);
    }
}
