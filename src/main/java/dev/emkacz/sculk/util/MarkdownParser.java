package dev.emkacz.sculk.util;

import java.util.regex.Pattern;

public final class MarkdownParser {

    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.*?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*(.*?)\\*");
    private static final Pattern UNDERLINE_PATTERN = Pattern.compile("__(.*?)__");
    private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~(.*?)~~");
    private static final Pattern CODE_PATTERN = Pattern.compile("`(.*?)`");

    private MarkdownParser() {
        // Prevent instantiation
    }

    /**
     * Translates standard markdown tags to Kyori MiniMessage tags.
     * We avoid matching single underscores (_) for italics, as items, blocks, 
     * and entities in Minecraft make heavy use of underscores (e.g., netherite_sword).
     */
    public static String toMiniMessage(String markdown) {
        if (markdown == null) {
            return "";
        }
        String result = markdown;
        
        // Convert bold (**text**)
        result = BOLD_PATTERN.matcher(result).replaceAll("<bold>$1</bold>");
        
        // Convert underline (__text__)
        result = UNDERLINE_PATTERN.matcher(result).replaceAll("<underlined>$1</underlined>");
        
        // Convert italic (*text*)
        result = ITALIC_PATTERN.matcher(result).replaceAll("<italic>$1</italic>");
        
        // Convert strikethrough (~~text~~)
        result = STRIKETHROUGH_PATTERN.matcher(result).replaceAll("<strikethrough>$1</strikethrough>");
        
        // Convert inline code (`text`)
        result = CODE_PATTERN.matcher(result).replaceAll("<gray>$1</gray>");
        
        return result;
    }
}
