package com.opencrm.configuration.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.configuration.dto.ConfigurationSessionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigurationSessionController - DTO API boundary")
class ConfigurationSessionControllerTest {

    private static final Set<String> FORBIDDEN_ENTITY_FIELDS = Set.of(
            "deleted", "isDeleted", "hibernateLazyInitializer", "handler"
    );

    @Mock
    private ConfigurationSessionService sessionService;

    @InjectMocks
    private ConfigurationSessionController sessionController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ConfigurationSessionDto sampleDto;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();
        mockMvc = MockMvcBuilders.standaloneSetup(sessionController)
                .setMessageConverters(converter)
                .build();

        sampleDto = ConfigurationSessionDto.builder()
                .id(1L)
                .uuid("uuid-1")
                .productId("PROD-001")
                .userId("user-1")
                .status(ConfigurationStatus.IN_PROGRESS)
                .selections(Map.of("color", "red"))
                .valid(true)
                .validationErrors(null)
                .expiresAt(Instant.parse("2026-09-13T12:00:00Z"))
                .createdAt(Instant.parse("2026-09-13T11:00:00Z"))
                .updatedAt(Instant.parse("2026-09-13T11:00:00Z"))
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("controller method signatures never expose ConfigurationSession entity")
    void controllerSignaturesUseDtoOnly() {
        for (Method method : ConfigurationSessionController.class.getDeclaredMethods()) {
            assertFalse(
                    mentionsConfigurationSessionEntity(method.getGenericReturnType()),
                    "Return type must not mention ConfigurationSession entity: " + method.getName()
            );
            for (Type param : method.getGenericParameterTypes()) {
                assertFalse(
                        mentionsConfigurationSessionEntity(param),
                        "Parameter must not mention ConfigurationSession entity: " + method.getName()
                );
            }
        }
    }

    @Test
    @DisplayName("POST / creates DTO without deleted")
    void createReturnsDtoOnly() throws Exception {
        when(sessionService.create("PROD-001", "user-1")).thenReturn(sampleDto);

        MvcResult result = mockMvc.perform(post("/api/v1/configurations")
                        .param("productId", "PROD-001")
                        .param("userId", "user-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.productId").value("PROD-001"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist())
                .andExpect(jsonPath("$.data.isDeleted").doesNotExist())
                .andReturn();

        assertNoEntityLeakFields(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("GET /{id} returns DTO JSON without BaseEntity deleted flag")
    void getByIdReturnsDtoOnly() throws Exception {
        when(sessionService.getById(1L)).thenReturn(sampleDto);

        MvcResult result = mockMvc.perform(get("/api/v1/configurations/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.uuid").value("uuid-1"))
                .andExpect(jsonPath("$.data.selections.color").value("red"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist())
                .andReturn();

        assertNoEntityLeakFields(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("GET / listByUser returns DTO array without deleted")
    void listByUserReturnsDtos() throws Exception {
        when(sessionService.getByUserAndStatus("user-1", ConfigurationStatus.IN_PROGRESS))
                .thenReturn(List.of(sampleDto));

        MvcResult result = mockMvc.perform(get("/api/v1/configurations")
                        .param("userId", "user-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value("user-1"))
                .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist())
                .andReturn();

        assertNoEntityLeakFields(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("POST selections / DELETE selection / validate / complete / cancel return DTOs")
    void mutationEndpointsReturnDto() throws Exception {
        when(sessionService.addSelection(1L, "size", "L")).thenReturn(sampleDto.toBuilder()
                .selections(Map.of("color", "red", "size", "L")).build());
        when(sessionService.removeSelection(1L, "color")).thenReturn(sampleDto.toBuilder()
                .selections(Map.of()).valid(false).build());
        when(sessionService.validate(1L)).thenReturn(sampleDto);
        when(sessionService.complete(1L)).thenReturn(sampleDto.toBuilder()
                .status(ConfigurationStatus.COMPLETED)
                .completedAt(Instant.parse("2026-09-13T11:30:00Z"))
                .build());
        when(sessionService.cancel(1L)).thenReturn(sampleDto.toBuilder()
                .status(ConfigurationStatus.CANCELLED).build());

        mockMvc.perform(post("/api/v1/configurations/1/selections")
                        .param("optionKey", "size")
                        .param("optionValue", "L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Selection added"))
                .andExpect(jsonPath("$.data.selections.size").value("L"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist());

        mockMvc.perform(delete("/api/v1/configurations/1/selections/color"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Selection removed"))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.deleted").doesNotExist());

        mockMvc.perform(post("/api/v1/configurations/1/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Configuration is valid"))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.deleted").doesNotExist());

        mockMvc.perform(post("/api/v1/configurations/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist());

        mockMvc.perform(post("/api/v1/configurations/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist());
    }

    @Test
    @DisplayName("session POST /{id}/validate stays and returns invalid message when not valid")
    void validateRetainedReturnsInvalidMessage() throws Exception {
        when(sessionService.validate(eq(2L))).thenReturn(sampleDto.toBuilder()
                .id(2L)
                .valid(false)
                .validationErrors("Color is required")
                .build());

        mockMvc.perform(post("/api/v1/configurations/2/validate")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Configuration has errors"))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.validationErrors").value("Color is required"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist());
    }

    private void assertNoEntityLeakFields(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        assertFalse(containsForbiddenField(root), "JSON must not contain entity leak fields: " + FORBIDDEN_ENTITY_FIELDS);
    }

    private boolean containsForbiddenField(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (FORBIDDEN_ENTITY_FIELDS.contains(name)) {
                    return true;
                }
                if (containsForbiddenField(node.get(name))) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            return StreamSupport.stream(node.spliterator(), false)
                    .anyMatch(this::containsForbiddenField);
        }
        return false;
    }

    private static boolean mentionsConfigurationSessionEntity(Type type) {
        if (type instanceof Class<?> clazz) {
            return ConfigurationSession.class.equals(clazz);
        }
        if (type instanceof ParameterizedType parameterized) {
            if (mentionsConfigurationSessionEntity(parameterized.getRawType())) {
                return true;
            }
            for (Type arg : parameterized.getActualTypeArguments()) {
                if (mentionsConfigurationSessionEntity(arg)) {
                    return true;
                }
            }
        }
        return false;
    }
}
