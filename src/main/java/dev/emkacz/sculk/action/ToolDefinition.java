package dev.emkacz.sculk.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Declarative descriptor for a single AI-callable tool. Pairs an OpenAI-style
 * JSON schema (used by {@code buildToolsDefinition}) with an executor that
 * runs the actual side effect.
 *
 * <p>To add a new tool: register one entry in {@link Tools#REGISTRY}. That's it.
 * The schema and the executor are colocated, so they cannot drift apart.
 */
public final class ToolDefinition {

    /** Executors must be main-thread safe — Bukkit API calls require it. */
    @FunctionalInterface
    public interface ToolExecutor {
        /**
         * @return a JSON object describing the result. {@code success: true/false}
         *         is the convention. Returning {@code null} is allowed when the
         *         tool has already messaged the player.
         */
        JsonObject execute(ToolContext ctx, JsonObject arguments) throws Exception;
    }

    private final String name;
    private final String description;
    private final Map<String, ParameterSpec> parameters;
    private final List<String> required;
    /** If non-null, the tool is only listed in the schema when this returns true. */
    private final Predicate<Player> visibility;
    private final ToolExecutor executor;

    public ToolDefinition(String name,
                          String description,
                          Map<String, ParameterSpec> parameters,
                          List<String> required,
                          Predicate<Player> visibility,
                          ToolExecutor executor) {
        this.name = name;
        this.description = description;
        this.parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        this.required = required == null ? List.of() : List.copyOf(required);
        this.visibility = visibility;
        this.executor = executor;
    }

    public String name() { return name; }
    public String description() { return description; }
    public Map<String, ParameterSpec> parameters() { return parameters; }
    public List<String> required() { return required; }
    public Predicate<Player> visibility() { return visibility; }
    public ToolExecutor executor() { return executor; }

    /** Build the OpenAI-style JSON schema for this tool. */
    public JsonObject toJsonSchema() {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);

        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        JsonObject props = new JsonObject();
        for (Map.Entry<String, ParameterSpec> e : parameters.entrySet()) {
            JsonObject spec = new JsonObject();
            spec.addProperty("type", e.getValue().type());
            spec.addProperty("description", e.getValue().description());
            props.add(e.getKey(), spec);
        }
        params.add("properties", props);
        if (!required.isEmpty()) {
            JsonArray req = new JsonArray();
            for (String r : required) req.add(r);
            params.add("required", req);
        }
        function.add("parameters", params);

        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("type", "function");
        wrapper.add("function", function);
        return wrapper;
    }

    /**
     * One parameter entry inside a tool's JSON schema.
     */
    public record ParameterSpec(String type, String description) {
        public static ParameterSpec string(String desc)  { return new ParameterSpec("string",  desc); }
        public static ParameterSpec integer(String desc) { return new ParameterSpec("integer", desc); }
        public static ParameterSpec number(String desc)  { return new ParameterSpec("number",  desc); }
        public static ParameterSpec boolean_(String desc){ return new ParameterSpec("boolean", desc); }
    }
}
