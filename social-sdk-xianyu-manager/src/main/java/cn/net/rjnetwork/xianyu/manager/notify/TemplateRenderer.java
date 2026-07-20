^package cn.net.rjnetwork.xianyu.manager.notify;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简模板渲染：将 {key} 占位符替换为 vars 中对应值。无 SpEL/Groovy，避免注入风险。
 */
public class TemplateRenderer {

    private static final Pattern PATTERN = Pattern.compile("\\{(\\w+)\\}");

    public static String render(String template, Map<String, Object> vars) {
        if (template == null || template.isEmpty()) return "";
        Matcher m = PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            Object val = vars != null ? vars.get(key) : null;
            String replacement = val != null ? String.valueOf(val) : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
