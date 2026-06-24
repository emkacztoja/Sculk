package dev.emkacz.sculk.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolDefinitionTest {

    @Test
    void allToolNamesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (ToolDefinition def : Tools.REGISTRY) {
            assertTrue(seen.add(def.name()), "Duplicate tool name: " + def.name());
        }
    }

    @Test
    void everyToolHasNonEmptyDescription() {
        for (ToolDefinition def : Tools.REGISTRY) {
            assertNotNull(def.description(), () -> "Tool " + def.name() + " has null description");
            assertFalse(def.description().isBlank(), () -> "Tool " + def.name() + " has blank description");
        }
    }

    @Test
    void allRequiredParamsAreInParameters() {
        for (ToolDefinition def : Tools.REGISTRY) {
            for (String req : def.required()) {
                assertTrue(def.parameters().containsKey(req),
                        () -> "Tool " + def.name() + " requires '" + req + "' but it is not declared in parameters");
            }
        }
    }

    @Test
    void jsonSchemaHasFunctionWrapper() {
        for (ToolDefinition def : Tools.REGISTRY) {
            JsonObject schema = def.toJsonSchema();
            assertEquals("function", schema.get("type").getAsString());
            JsonObject function = schema.getAsJsonObject("function");
            assertEquals(def.name(), function.get("name").getAsString());
            assertEquals(def.description(), function.get("description").getAsString());
            assertNotNull(function.get("parameters"));
        }
    }

    @Test
    void jsonSchemaEmptyToolHasEmptyProperties() {
        ToolDefinition empty = new ToolDefinition(
                "empty_tool", "Does nothing.",
                java.util.Map.of(), java.util.List.of(),
                null,
                (ctx, args) -> new JsonObject());
        JsonObject schema = empty.toJsonSchema();
        JsonObject params = schema.getAsJsonObject("function").getAsJsonObject("parameters");
        JsonObject props = params.getAsJsonObject("properties");
        assertEquals(0, props.size());
        assertFalse(params.has("required"));
    }

    @Test
    void jsonSchemaWithRequiredListsThem() {
        ToolDefinition t = new ToolDefinition(
                "with_req", "Has required params.",
                java.util.Map.of(
                        "a", ToolDefinition.ParameterSpec.string("param a"),
                        "b", ToolDefinition.ParameterSpec.integer("param b")),
                java.util.List.of("a"),
                null,
                (ctx, args) -> new JsonObject());
        JsonObject params = t.toJsonSchema().getAsJsonObject("function").getAsJsonObject("parameters");
        JsonArray required = params.getAsJsonArray("required");
        assertEquals(1, required.size());
        assertEquals("a", required.get(0).getAsString());
    }

    @Test
    void atLeastOneToolRequiresSudo() {
        // Sanity check: the visibility-gated tools are actually in the registry
        boolean foundSudoTool = false;
        for (ToolDefinition def : Tools.REGISTRY) {
            if (def.visibility() != null) {
                foundSudoTool = true;
                break;
            }
        }
        assertTrue(foundSudoTool, "Registry should contain at least one visibility-gated tool");
    }

    @Test
    void sacrificeToolHasTransactionalAnnotation() {
        // The sacrifice tool's description must mention transactional safety
        for (ToolDefinition def : Tools.REGISTRY) {
            if ("sacrifice_held_item".equals(def.name())) {
                assertTrue(def.description().toLowerCase().contains("transactional")
                                || def.description().toLowerCase().contains("not consumed"),
                        "sacrifice_held_item description should mention transactional safety");
                return;
            }
        }
        fail("sacrifice_held_item not found in registry");
    }
}
