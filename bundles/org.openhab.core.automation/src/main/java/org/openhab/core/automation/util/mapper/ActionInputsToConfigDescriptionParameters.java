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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.type.Input;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.ConfigDescriptionParameterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a utility class to convert action {@link Input}s to {@link ConfigDescriptionParameter}s.
 *
 * @author Laurent Garnier - Initial contribution
 */
@NonNullByDefault
public class ActionInputsToConfigDescriptionParameters {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionInputsToConfigDescriptionParameters.class);

    /**
     * Maps a list of {@link Input} to a list of {@link ConfigDescriptionParameter}.
     *
     * @param inputs the list of inputs to map to config description parameters
     * @return the list of config description parameters or null if an input parameter has an unsupported type
     */
    public static @Nullable List<ConfigDescriptionParameter> map(List<Input> inputs) {
        List<ConfigDescriptionParameter> configDescriptionParameters = new ArrayList<>();

        for (Input input : inputs) {
            ConfigDescriptionParameter parameter = ActionInputsToConfigDescriptionParameters.map(input);
            if (parameter != null) {
                configDescriptionParameters.add(parameter);
            } else {
                configDescriptionParameters = null;
                break;
            }
        }

        return configDescriptionParameters;
    }

    /**
     * Maps an {@link Input} to a {@link ConfigDescriptionParameter}.
     *
     * @param input the input to map to a config description parameter
     * @return the config description parameter or null if the input parameter has an unsupported type
     */
    public static @Nullable ConfigDescriptionParameter map(Input input) {
        boolean supported = true;
        ConfigDescriptionParameter.Type parameterType = ConfigDescriptionParameter.Type.TEXT;
        String defaultValue = null;
        boolean required = false;
        String context = null;
        switch (input.getType()) {
            case "boolean":
                defaultValue = "false";
                required = true;
            case "java.lang.Boolean":
                parameterType = ConfigDescriptionParameter.Type.BOOLEAN;
                break;
            case "byte":
            case "short":
            case "int":
            case "long":
                defaultValue = "0";
                required = true;
            case "java.lang.Byte":
            case "java.lang.Short":
            case "java.lang.Integer":
            case "java.lang.Long":
                parameterType = ConfigDescriptionParameter.Type.INTEGER;
                break;
            case "float":
            case "double":
                defaultValue = "0";
                required = true;
            case "java.lang.Float":
            case "java.lang.Double":
                parameterType = ConfigDescriptionParameter.Type.DECIMAL;
                break;
            case "java.lang.String":
                break;
            case "java.time.LocalDate":
                context = "date";
                break;
            case "java.time.LocalTime":
                context = "time";
                break;
            case "java.time.LocalDateTime":
            case "java.time.ZonedDateTime":
                context = "datetime";
                break;
            case "org.openhab.core.library.types.QuantityType":
                break;
            default:
                supported = false;
                break;
        }
        if (!supported) {
            LOGGER.info("Unsupported input parameter '{}' having type {}", input.getName(), input.getType());
            return null;
        }

        ConfigDescriptionParameterBuilder builder = ConfigDescriptionParameterBuilder
                .create(input.getName(), parameterType).withLabel(input.getLabel())
                .withDescription(input.getDescription()).withReadOnly(false)
                .withRequired(required || input.isRequired()).withContext(context);
        if (!input.getDefaultValue().isEmpty()) {
            builder = builder.withDefault(input.getDefaultValue());
        } else if (defaultValue != null) {
            builder = builder.withDefault(defaultValue);
        }
        return builder.build();
    }
}
