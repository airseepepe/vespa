// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A set of fields that are imported from referenced document types.
 *
 * @author geirst
 */
public class ImportedFields {

    private final Map<String, TemporaryImportedField> fields = new LinkedHashMap<>();

    public void add(TemporaryImportedField importedField) {
        fields.put(importedField.fieldName(), importedField);
    }

    public Map<String, TemporaryImportedField> fields() {
        return Collections.unmodifiableMap(fields);
    }
}
