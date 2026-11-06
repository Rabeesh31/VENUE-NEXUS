package com.venuenexus.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeContractSyncTest {

    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\|\\s*`([A-Z0-9_]+)`\\s*(?:\\[[^\\]]+\\])?\\s*\\|\\s*(\\d{3})\\s*\\|.*");

    @Test
    @DisplayName("ErrorCode enum values must match the error catalogue in docs/api-contract.md exactly")
    void testEnumMatchesContractCatalogue() throws IOException {
        Path contractPath = findContractFile();
        assertThat(contractPath).isNotNull().exists();

        List<String> lines = Files.readAllLines(contractPath);
        Set<String> documentedErrorCodes = new TreeSet<>();

        for (String line : lines) {
            Matcher matcher = TABLE_ROW_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                String code = matcher.group(1);
                documentedErrorCodes.add(code);
            }
        }

        assertThat(documentedErrorCodes).isNotEmpty();

        Set<String> enumErrorCodes = new TreeSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            enumErrorCodes.add(code.name());
        }

        // Verify bidirectional synchronization
        Set<String> inEnumButNotDocumented = new TreeSet<>(enumErrorCodes);
        inEnumButNotDocumented.removeAll(documentedErrorCodes);

        Set<String> documentedButNotInEnum = new TreeSet<>(documentedErrorCodes);
        documentedButNotInEnum.removeAll(enumErrorCodes);

        assertThat(inEnumButNotDocumented)
                .as("ErrorCode enum contains codes not documented in docs/api-contract.md")
                .isEmpty();

        assertThat(documentedButNotInEnum)
                .as("docs/api-contract.md documents codes not implemented in ErrorCode enum")
                .isEmpty();
    }

    private Path findContractFile() {
        // Handle execution from repo root or backend folder
        Path candidate1 = Paths.get("docs", "api-contract.md");
        if (Files.exists(candidate1)) {
            return candidate1;
        }
        Path candidate2 = Paths.get("..", "docs", "api-contract.md");
        if (Files.exists(candidate2)) {
            return candidate2;
        }
        return Paths.get(System.getProperty("user.dir")).getParent().resolve("docs").resolve("api-contract.md");
    }
}
