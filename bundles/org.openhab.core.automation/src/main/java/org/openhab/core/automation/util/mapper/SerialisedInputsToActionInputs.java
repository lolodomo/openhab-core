/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.automation.util.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.type.ActionType;
import org.openhab.core.automation.type.Input;

/**
 * This is a utility class to convert serialised inputs to the Java types required by the {@link Input}s of a
 * {@link ActionType}.
 *
 * @author Laurent Garnier & Florian Hotze - Initial contribution
 */
@NonNullByDefault
public class SerialisedInputsToActionInputs {
    /**
     * Maps serialised inputs to the Java types required by the {@link Input}s of the given {@link ActionType}.
     *
     * @param actionType the action type whose inputs to consider
     * @param arguments the serialised arguments
     * @return the mapped arguments
     */
    public static Map<String, Object> map(ActionType actionType, Map<String, Object> arguments) {
        Map<String, Object> newArguments = new HashMap<>();
        for (Input input : actionType.getInputs()) {
            String name = input.getName();
            Object value = arguments.get(name);
            value = map(input, value);
            if (value == null) {
                continue;
            }
            newArguments.put(name, value);
        }
        return newArguments;
    }

    /**
     * Maps a serialised input to the Java type required by the given {@link Input}.
     *
     * @param input the input whose type to consider
     * @param argument the serialised argument
     * @return the mapped argument or null if the input argument was null
     */
    public static @Nullable Object map(Input input, Object argument) {
        if (argument == null) {
            return null;
        }
        return switch (input.getType()) {
            case "byte", "java.lang.Byte" -> {
                if (argument instanceof Double valueDouble) {
                    yield Byte.valueOf(valueDouble.byteValue());
                } else if (argument instanceof String valueString) {
                    yield Byte.valueOf(valueString);
                } else {
                    yield argument;
                }
            }
            case "short", "java.lang.Short" -> {
                if (argument instanceof Double valueDouble) {
                    yield Short.valueOf(valueDouble.shortValue());
                } else if (argument instanceof String valueString) {
                    yield Short.valueOf(valueString);
                } else {
                    yield argument;
                }
            }
            case "int", "java.lang.Integer" -> {
                if (argument instanceof Double valueDouble) {
                    yield Integer.valueOf(valueDouble.intValue());
                } else if (argument instanceof String valueString) {
                    yield Integer.valueOf(valueString);
                } else {
                    yield argument;
                }
            }
            case "long", "java.lang.Long" -> {
                if (argument instanceof Double valueDouble) {
                    yield Long.valueOf(valueDouble.longValue());
                } else if (argument instanceof String valueString) {
                    yield Long.valueOf(valueString);
                } else {
                    yield argument;
                }
            }
            case "float", "java.lang.Float" -> {
                if (argument instanceof Double valueDouble) {
                    yield Float.valueOf(valueDouble.floatValue());
                } else if (argument instanceof String valueString) {
                    yield Float.valueOf(valueString);
                } else {
                    yield argument;
                }
            }
            case "double", "java.lang.Double" -> {
                if (argument instanceof String valueString) {
                    yield Double.valueOf(valueString);
                } else {
                    yield argument;
                }
            }
            case "java.time.LocalDate" -> {
                if (argument instanceof String valueString) {
                    yield LocalDate.parse(valueString);
                } else {
                    yield argument;
                }
            }
            case "java.time.LocalTime" -> {
                if (argument instanceof String valueString) {
                    yield LocalTime.parse(valueString);
                } else {
                    yield argument;
                }
            }
            case "java.time.LocalDateTime" -> {
                if (argument instanceof String valueString) {
                    yield LocalDateTime.parse(valueString);
                } else {
                    yield argument;
                }
            }
            case "java.time.ZonedDateTime" -> {
                if (argument instanceof String valueString) {
                    yield ZonedDateTime.parse(valueString);
                } else {
                    yield argument;
                }
            }
            default -> argument;
        };
    }
}
