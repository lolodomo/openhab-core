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
package org.openhab.core.automation.rest.internal;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.auth.Role;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.automation.handler.ActionHandler;
import org.openhab.core.automation.handler.ModuleHandlerFactory;
import org.openhab.core.automation.type.ActionType;
import org.openhab.core.automation.type.Input;
import org.openhab.core.automation.type.ModuleTypeRegistry;
import org.openhab.core.automation.type.Output;
import org.openhab.core.automation.util.ModuleBuilder;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.ConfigDescriptionParameterBuilder;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.core.dto.ConfigDescriptionDTOMapper;
import org.openhab.core.config.core.dto.ConfigDescriptionParameterDTO;
import org.openhab.core.io.rest.LocaleService;
import org.openhab.core.io.rest.RESTConstants;
import org.openhab.core.io.rest.RESTResource;
import org.openhab.core.io.rest.Stream2JSONInputStream;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JSONRequired;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsApplicationSelect;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsName;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The {@link ThingActionsResource} allows retrieving and executing thing actions via REST API
 *
 * @author Jan N. Klug - Initial contribution
 */
@Component
@JaxrsResource
@JaxrsName(ThingActionsResource.PATH_THINGS)
@JaxrsApplicationSelect("(" + JaxrsWhiteboardConstants.JAX_RS_NAME + "=" + RESTConstants.JAX_RS_NAME + ")")
@JSONRequired
@Path(ThingActionsResource.PATH_THINGS)
@Tag(name = ThingActionsResource.PATH_THINGS)
@NonNullByDefault
public class ThingActionsResource implements RESTResource {
    public static final String PATH_THINGS = "actions";

    private final Logger logger = LoggerFactory.getLogger(ThingActionsResource.class);

    private final LocaleService localeService;
    private final ModuleTypeRegistry moduleTypeRegistry;

    Map<ThingUID, Map<String, ThingActions>> thingActionsMap = new ConcurrentHashMap<>();
    private List<ModuleHandlerFactory> moduleHandlerFactories = new ArrayList<>();

    @Activate
    public ThingActionsResource(@Reference LocaleService localeService,
            @Reference ModuleTypeRegistry moduleTypeRegistry) {
        this.localeService = localeService;
        this.moduleTypeRegistry = moduleTypeRegistry;
    }

    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.MULTIPLE)
    public void addThingActions(ThingActions thingActions) {
        ThingHandler handler = thingActions.getThingHandler();
        String scope = getScope(thingActions);
        if (handler != null && scope != null) {
            ThingUID thingUID = handler.getThing().getUID();
            thingActionsMap.computeIfAbsent(thingUID, thingUid -> new ConcurrentHashMap<>()).put(scope, thingActions);
        }
    }

    public void removeThingActions(ThingActions thingActions) {
        ThingHandler handler = thingActions.getThingHandler();
        String scope = getScope(thingActions);
        if (handler != null && scope != null) {
            ThingUID thingUID = handler.getThing().getUID();
            Map<String, ThingActions> actionMap = thingActionsMap.get(thingUID);
            if (actionMap != null) {
                actionMap.remove(scope);
                if (actionMap.isEmpty()) {
                    thingActionsMap.remove(thingUID);
                }
            }
        }
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    protected void addModuleHandlerFactory(ModuleHandlerFactory moduleHandlerFactory) {
        moduleHandlerFactories.add(moduleHandlerFactory);
    }

    protected void removeModuleHandlerFactory(ModuleHandlerFactory moduleHandlerFactory) {
        moduleHandlerFactories.remove(moduleHandlerFactory);
    }

    @GET
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/{thingUID}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getAvailableActionsForThing", summary = "Get all available actions for provided thing UID", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ThingActionDTO.class), uniqueItems = true))),
            @ApiResponse(responseCode = "204", description = "No actions found.") })
    public Response getActions(@PathParam("thingUID") @Parameter(description = "thingUID") String thingUID,
            @HeaderParam(HttpHeaders.ACCEPT_LANGUAGE) @Parameter(description = "language") @Nullable String language) {
        Locale locale = localeService.getLocale(language);
        ThingUID aThingUID = new ThingUID(thingUID);

        List<ThingActionDTO> actions = new ArrayList<>();
        Map<String, ThingActions> thingActionsMap = this.thingActionsMap.get(aThingUID);
        if (thingActionsMap == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // inspect ThingActions
        for (Map.Entry<String, ThingActions> thingActionsEntry : thingActionsMap.entrySet()) {
            ThingActions thingActions = thingActionsEntry.getValue();
            Method[] methods = thingActions.getClass().getDeclaredMethods();
            for (Method method : methods) {
                RuleAction ruleAction = method.getAnnotation(RuleAction.class);

                if (ruleAction == null) {
                    continue;
                }

                String actionUid = thingActionsEntry.getKey() + "." + method.getName();
                ActionType actionType = (ActionType) moduleTypeRegistry.get(actionUid, locale);
                if (actionType == null) {
                    continue;
                }

                List<ConfigDescriptionParameter> inputParameters = new ArrayList<>();
                for (Input input : actionType.getInputs()) {
                    ConfigDescriptionParameter parameter = convertToConfigDescriptionParameter(input);
                    if (parameter != null) {
                        inputParameters.add(parameter);
                    } else {
                        inputParameters = null;
                        break;
                    }
                }

                ThingActionDTO actionDTO = new ThingActionDTO();
                actionDTO.actionUid = actionType.getUID();
                actionDTO.description = actionType.getDescription();
                actionDTO.label = actionType.getLabel();
                actionDTO.inputs = actionType.getInputs();
                actionDTO.inputConfigDescriptions = inputParameters == null ? null
                        : ConfigDescriptionDTOMapper.mapParameters(inputParameters);
                actionDTO.outputs = actionType.getOutputs();
                actions.add(actionDTO);
            }
        }

        return Response.ok().entity(new Stream2JSONInputStream(actions.stream())).build();
    }

    @POST
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/{thingUID}/{actionUid: [a-zA-Z0-9]+\\.[a-zA-Z0-9]+}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "executeThingAction", summary = "Executes a thing action.", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "404", description = "Action not found"),
            @ApiResponse(responseCode = "500", description = "Creation of action handler or execution failed") })
    public Response executeThingAction(@PathParam("thingUID") @Parameter(description = "thingUID") String thingUID,
            @PathParam("actionUid") @Parameter(description = "action type UID (including scope, separated by '.')") String actionTypeUid,
            @HeaderParam(HttpHeaders.ACCEPT_LANGUAGE) @Parameter(description = "language") @Nullable String language,
            @Parameter(description = "action inputs as map (parameter name as key / argument as value)") Map<String, Object> actionInputs) {
        ActionType actionType = (ActionType) moduleTypeRegistry.get(actionTypeUid);
        if (actionType == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String ruleUID = UUID.randomUUID().toString();

        Configuration configuration = new Configuration();
        configuration.put("config", thingUID);
        Action action = ModuleBuilder.createAction().withConfiguration(configuration)
                .withId(UUID.randomUUID().toString()).withTypeUID(actionTypeUid).build();

        ModuleHandlerFactory moduleHandlerFactory = moduleHandlerFactories.stream()
                .filter(f -> f.getTypes().contains(actionTypeUid)).findFirst().orElse(null);
        if (moduleHandlerFactory == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        ActionHandler handler = (ActionHandler) moduleHandlerFactory.getHandler(action, ruleUID);
        if (handler == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        try {
            Map<String, Object> returnValue = Objects.requireNonNullElse(
                    handler.execute(adjustTypForfMethodArguments(actionType, actionInputs)), Map.of());
            moduleHandlerFactory.ungetHandler(action, ruleUID, handler);
            return Response.ok(returnValue).build();
        } catch (Exception e) {
            moduleHandlerFactory.ungetHandler(action, ruleUID, handler);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
        }
    }

    private @Nullable ConfigDescriptionParameter convertToConfigDescriptionParameter(Input input) {
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
                parameterType = ConfigDescriptionParameter.Type.TEXT;
                break;
            case "java.time.LocalDate":
                parameterType = ConfigDescriptionParameter.Type.TEXT;
                context = "date";
                break;
            case "java.time.LocalTime":
                parameterType = ConfigDescriptionParameter.Type.TEXT;
                context = "time";
                break;
            case "java.time.LocalDateTime":
            case "java.time.ZonedDateTime":
                parameterType = ConfigDescriptionParameter.Type.TEXT;
                context = "datetime";
                break;
            default:
                supported = false;
                break;
        }
        if (!supported) {
            logger.warn("Unsupported input parameter '{}' having type {}", input.getName(), input.getType());
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

    private Map<String, Object> adjustTypForfMethodArguments(ActionType actionType, Map<String, Object> arguments) {
        Map<String, Object> newArguments = new HashMap<>();
        for (Input input : actionType.getInputs()) {
            String name = input.getName();
            Object value = arguments.get(name);
            if (value == null) {
                continue;
            }
            switch (input.getType()) {
                case "byte":
                case "java.lang.Byte":
                    if (value instanceof Double valueDouble) {
                        newArguments.put(name, Byte.valueOf(valueDouble.byteValue()));
                    } else if (value instanceof String valueString) {
                        newArguments.put(name, Byte.valueOf(valueString));
                    } else {
                        newArguments.put(name, value);
                    }
                    break;
                case "short":
                case "java.lang.Short":
                    if (value instanceof Double valueDouble) {
                        newArguments.put(name, Short.valueOf(valueDouble.shortValue()));
                    } else if (value instanceof String valueString) {
                        newArguments.put(name, Short.valueOf(valueString));
                    } else {
                        newArguments.put(name, value);
                    }
                    break;
                case "int":
                case "java.lang.Integer":
                    if (value instanceof Double valueDouble) {
                        newArguments.put(name, Integer.valueOf(valueDouble.intValue()));
                    } else if (value instanceof String valueString) {
                        newArguments.put(name, Integer.valueOf(valueString));
                    } else {
                        newArguments.put(name, value);
                    }
                    break;
                case "long":
                case "java.lang.Long":
                    if (value instanceof Double valueDouble) {
                        newArguments.put(name, Long.valueOf(valueDouble.longValue()));
                    } else if (value instanceof String valueString) {
                        newArguments.put(name, Long.valueOf(valueString));
                    } else {
                        newArguments.put(name, value);
                    }
                    break;
                case "float":
                case "java.lang.Float":
                    if (value instanceof Double valueDouble) {
                        newArguments.put(name, Float.valueOf(valueDouble.floatValue()));
                    } else if (value instanceof String valueString) {
                        newArguments.put(name, Float.valueOf(valueString));
                    } else {
                        newArguments.put(name, value);
                    }
                    break;
                case "double":
                case "java.lang.Double":
                    if (value instanceof String valueString) {
                        newArguments.put(name, Double.valueOf(valueString));
                    } else {
                        newArguments.put(name, value);
                    }
                    break;
                case "java.time.LocalDate":
                    if (value instanceof String valueString) {
                        newArguments.put(name, LocalDate.parse(valueString));
                    } else {
                        newArguments.put(name, value);
                    }
                    break;
                case "java.time.LocalTime":
                    if (value instanceof String valueString) {
                        newArguments.put(name, LocalTime.parse(valueString));
                    } else {
                        newArguments.put(name, value);
                    }
                    break;
                case "java.time.LocalDateTime":
                    if (value instanceof String valueString) {
                        newArguments.put(name, LocalDateTime.parse(valueString));
                    } else {
                        newArguments.put(name, value);
                    }
                    break;
                case "java.time.ZonedDateTime":
                    if (value instanceof String valueString) {
                        newArguments.put(name, ZonedDateTime.parse(valueString));
                    } else {
                        newArguments.put(name, value);
                    }
                    break;
                default:
                    newArguments.put(name, value);
                    break;
            }
        }
        return newArguments;
    }

    private @Nullable String getScope(ThingActions actions) {
        ThingActionsScope scopeAnnotation = actions.getClass().getAnnotation(ThingActionsScope.class);
        if (scopeAnnotation == null) {
            return null;
        }
        return scopeAnnotation.name();
    }

    private static class ThingActionDTO {
        public String actionUid = "";

        public @Nullable String label;
        public @Nullable String description;

        public List<Input> inputs = new ArrayList<>();

        public @Nullable List<ConfigDescriptionParameterDTO> inputConfigDescriptions;

        public List<Output> outputs = new ArrayList<>();
    }
}
