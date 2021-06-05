package com.schlaikjer.music.exception;

import java.util.Locale;

public class MissingMigrationException extends RuntimeException {

    public MissingMigrationException(String dbName, int migrationNumber) {
        super(String.format(Locale.ENGLISH, "Missing migration %d for db '%s'", migrationNumber, dbName));
    }

}
