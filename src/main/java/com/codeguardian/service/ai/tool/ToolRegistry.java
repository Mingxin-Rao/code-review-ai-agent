package com.codeguardian.service.ai.tool;

import com.codeguardian.service.ai.dto.ToolDefinition;
import com.codeguardian.service.ai.dto.FunctionDefinition;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;
import org.springframework.ai.model.function.FunctionCallback;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Service
@Slf4j
@RequiredArgsConstructor
public class ToolRegistry {

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final Map<String, ToolWrapper> tools = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // scan all beans of type Function
        String[] beanNames = applicationContext.getBeanNamesForType(Function.class);
        
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            
            Description description = applicationContext.findAnnotationOnBean(beanName, Description.class);
            if (description != null) {
                registerFunctionBean(beanName, (Function<?, ?>) bean, description.value());
            }
        }
    }

    private void registerFunctionBean(String name, Function<?, ?> function, String description) {
        try {
            // use reflection to find the input type
            Method applyMethod = Function.class.getMethod("apply", Object.class);
            // because of generic type erasure, obtaining the generic type is complex.
            // simplified strategy: assume the function bean declares a concrete type in its configuration

            // for now, try inspecting the implementation class or interface
            Type[] genericInterfaces = function.getClass().getGenericInterfaces();
            // this may not work for lambda expressions or proxy objects.
            
            // provider-specific handling for known beans (temporary approach)
            Class<?> inputType = Object.class;
            
            // provider-specific handling for known beans (temporary approach)
            if (name.equals("javaSyntaxAnalysis")) {
                inputType = com.codeguardian.service.ai.tools.JavaSyntaxAnalyzerTool.Request.class;
            } else if (name.equals("semgrepAnalysis")) {
                inputType = com.codeguardian.service.ai.tools.SemgrepAnalyzerTool.Request.class;
            } else {
                // try to infer the type from the generic interface, otherwise skip
                // in a real framework, this logic would need to be more robust
                try {
                     for (Type type : genericInterfaces) {
                         if (type instanceof ParameterizedType) {
                             ParameterizedType pt = (ParameterizedType) type;
                             if (pt.getRawType().equals(Function.class)) {
                                 Type[] args = pt.getActualTypeArguments();
                                 if (args.length > 0 && args[0] instanceof Class) {
                                     inputType = (Class<?>) args[0];
                                     break;
                                 }
                             }
                         }
                     }
                     if (inputType == Object.class) {
                         log.warn("Unknown function bean {}, skipping schema generation", name);
                         return;
                     }
                } catch (Exception e) {
                    log.warn("Cannot infer input type for {}, skipping", name);
                    return;
                }
            }

            Map<String, Object> parameters = generateJsonSchema(inputType);
            
            ToolDefinition toolDefinition = ToolDefinition.builder()
                    .type("function")
                    .function(FunctionDefinition.builder()
                            .name(name)
                            .description(description)
                            .parameters(parameters)
                            .build())
                    .build();
            
            tools.put(name, new ToolWrapper(toolDefinition, function, inputType));
            log.info("Registered tool: {}", name);

        } catch (Exception e) {
            log.error("Failed to register tool {}", name, e);
        }
    }

    private Map<String, Object> generateJsonSchema(Class<?> clazz) {
        try {
            // use a simplified JSON Schema generator based on Jackson
            // note: ideally jackson-module-jsonSchema or a similar library should be used
            // here we manually build a simple schema for the record class
            
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            
            Map<String, Object> properties = new HashMap<>();
            List<String> required = new ArrayList<>();
            
            for (Field field : clazz.getDeclaredFields()) {
                Map<String, Object> prop = new HashMap<>();
                prop.put("type", "string"); // default to the String type
                
                if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                    prop.put("type", "integer");
                } else if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
                    prop.put("type", "boolean");
                }
                
                JsonPropertyDescription desc = field.getAnnotation(JsonPropertyDescription.class);
                if (desc != null) {
                    prop.put("description", desc.value());
                }
                
                JsonProperty jsonProp =
                        field.getAnnotation(JsonProperty.class);
                if (jsonProp != null && jsonProp.required()) {
                    required.add(field.getName());
                }
                
                // add to the properties list
                properties.put(field.getName(), prop);
            }
            
            schema.put("properties", properties);
            if (!required.isEmpty()) {
                schema.put("required", required);
            }
            
            return schema;
        } catch (Exception e) {
            log.error("Failed to generate schema for {}", clazz, e);
            return Map.of();
        }
    }

    public List<ToolDefinition> getTools() {
        return tools.values().stream()
                .map(ToolWrapper::getDefinition)
                .collect(java.util.stream.Collectors.toList());
    }

    public java.util.Set<String> getToolNames() {
        return tools.keySet();
    }

    public List<FunctionCallback> getFunctionCallbacks() {
        return tools.values().stream()
                .map(wrapper -> new FunctionCallback() {
                    @Override
                    public String getName() {
                        return wrapper.getDefinition().getFunction().getName();
                    }

                    @Override
                    public String getDescription() {
                        return wrapper.getDefinition().getFunction().getDescription();
                    }

                    @Override
                    public String getInputTypeSchema() {
                        try {
                            return objectMapper.writeValueAsString(wrapper.getDefinition().getFunction().getParameters());
                        } catch (Exception e) {
                            log.error("Failed to serialize schema for tool {}", getName(), e);
                            return "{}";
                        }
                    }

                    @Override
                    public String call(String functionInput) {
                        log.info("[Function Calling] received a model call request: tool={}, args={}", getName(), functionInput);
                        try {
                            Object result = execute(getName(), functionInput);
                            return objectMapper.writeValueAsString(result);
                        } catch (Exception e) {
                            log.error("Failed to execute or serialize result for tool {}", getName(), e);
                            throw new RuntimeException(e);
                        }
                    }
                })
                .collect(java.util.stream.Collectors.toList());
    }

    public Object execute(String toolName, String arguments) {
        ToolWrapper wrapper = tools.get(toolName);
        if (wrapper == null) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }
        
        try {
            Object input = objectMapper.readValue(arguments, wrapper.getInputType());
            return wrapper.getFunction().apply((Object)input); // Cast to raw Object to satisfy compiler
        } catch (Exception e) {
            log.error("Error executing tool {}", toolName, e);
            throw new RuntimeException("Tool execution failed: " + e.getMessage());
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ToolWrapper {
        private ToolDefinition definition;
        private Function function;
        private Class<?> inputType;
    }
}
