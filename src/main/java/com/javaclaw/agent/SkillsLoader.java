package com.javaclaw.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 技能加载：workspace/skills/&lt;name&gt;/SKILL.md 与内置 builtinSkillsDir/&lt;name&gt;/SKILL.md。
 */
public class SkillsLoader {

    private static final String SKILL_FILE = "SKILL.md";
    private static final String SKILLS_DIR = "skills";

    private final Path workspaceSkillsDir;
    private final Path builtinSkillsDir;

    public SkillsLoader(Path workspace, Path builtinSkillsDir) {
        this.workspaceSkillsDir = workspace.resolve(SKILLS_DIR);
        this.builtinSkillsDir = builtinSkillsDir != null ? builtinSkillsDir : null;
    }

    /** 列出技能，每项含 name、path、source（workspace/builtin）；filterUnavailable 为 true 时过滤依赖未满足的 */
    public List<Map<String, String>> listSkills(boolean filterUnavailable) {
        List<Map<String, String>> out = new ArrayList<>();
        if (Files.isDirectory(workspaceSkillsDir)) {
            try {
                Files.list(workspaceSkillsDir).filter(Files::isDirectory).forEach(dir -> {
                    Path f = dir.resolve(SKILL_FILE);
                    if (Files.isRegularFile(f)) {
                        Map<String, String> m = new HashMap<>();
                        m.put("name", dir.getFileName().toString());
                        m.put("path", f.toString());
                        m.put("source", "workspace");
                        out.add(m);
                    }
                });
            } catch (IOException e) {
                // ignore
            }
        }
        if (builtinSkillsDir != null && Files.isDirectory(builtinSkillsDir)) {
            try {
                Files.list(builtinSkillsDir).filter(Files::isDirectory).forEach(dir -> {
                    Path f = dir.resolve(SKILL_FILE);
                    if (Files.isRegularFile(f)) {
                        String name = dir.getFileName().toString();
                        if (out.stream().noneMatch(m -> name.equals(m.get("name")))) {
                            Map<String, String> m = new HashMap<>();
                            m.put("name", name);
                            m.put("path", f.toString());
                            m.put("source", "builtin");
                            out.add(m);
                        }
                    }
                });
            } catch (IOException e) {
                // ignore
            }
        }
        if (filterUnavailable) {
            List<Map<String, String>> filtered = new ArrayList<>();
            for (Map<String, String> m : out) {
                Optional<Map<String, Object>> meta = getSkillMetadata(m.get("name"));
                if (!meta.isPresent() || !meta.get().containsKey("available") || Boolean.TRUE.equals(meta.get().get("available"))) {
                    filtered.add(m);
                }
            }
            return filtered;
        }
        return out;
    }

    /** 按名称读取 SKILL.md 内容；先查 workspace 再 builtin */
    public Optional<String> loadSkill(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }
        Path inWorkspace = workspaceSkillsDir.resolve(name).resolve(SKILL_FILE);
        if (Files.isRegularFile(inWorkspace)) {
            try {
                return Optional.of(new String(Files.readAllBytes(inWorkspace), StandardCharsets.UTF_8));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        if (builtinSkillsDir != null) {
            Path inBuiltin = builtinSkillsDir.resolve(name).resolve(SKILL_FILE);
            if (Files.isRegularFile(inBuiltin)) {
                try {
                    return Optional.of(new String(Files.readAllBytes(inBuiltin), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /** 将多个技能内容拼接成一段文本，供 system prompt 使用 */
    public String loadSkillsForContext(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String name : skillNames) {
            loadSkill(name).ifPresent(s -> sb.append("### ").append(name).append("\n\n").append(s).append("\n\n"));
        }
        return sb.toString();
    }

    /** 生成技能摘要（名称与描述），供 system prompt 中“可用技能列表”使用 */
    public String buildSkillsSummary() {
        List<Map<String, String>> list = listSkills(false);
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Available skills: ");
        List<String> names = new ArrayList<>();
        for (Map<String, String> m : list) {
            names.add(m.get("name"));
        }
        sb.append(String.join(", ", names)).append("\n");
        return sb.toString();
    }

    /** 返回需常驻加载的技能名列表（由 SKILL 元数据决定；暂无元数据时返回所有技能） */
    public List<String> getAlwaysSkills() {
        List<Map<String, String>> skills = listSkills(false);
        List<String> skillNames = new ArrayList<>();
        for (Map<String, String> skill : skills) {
            skillNames.add(skill.get("name"));
        }
        return skillNames;
    }

    /** 返回技能元数据（如依赖、available 等）；简单实现可只返回 empty */
    public Optional<Map<String, Object>> getSkillMetadata(String name) {
        Optional<String> content = loadSkill(name);
        if (!content.isPresent()) {
            return Optional.empty();
        }
        return parseMetadata(content.get());
    }

    /** 从 SKILL.md 解析元数据（name, description, available, tools） */
    private Optional<Map<String, Object>> parseMetadata(String content) {
        Map<String, Object> meta = new HashMap<>();
        String[] lines = content.split("\n");
        boolean inFrontMatter = false;
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : lines) {
            if (line.trim().equals("---")) {
                if (inFrontMatter) {
                    // 结束front matter
                    if (currentKey != null && currentValue.length() > 0) {
                        String value = currentValue.toString().trim();
                        if (value.startsWith("[") && value.endsWith("]")) {
                            // 解析数组
                            List<String> items = new ArrayList<>();
                            String arrayContent = value.substring(1, value.length() - 1);
                            for (String item : arrayContent.split(",")) {
                                String trimmed = item.trim();
                                if (!trimmed.isEmpty()) {
                                    items.add(trimmed);
                                }
                            }
                            meta.put(currentKey, items);
                        } else {
                            meta.put(currentKey, value);
                        }
                    }
                    break;
                } else {
                    inFrontMatter = true;
                    currentKey = null;
                    currentValue = new StringBuilder();
                }
                continue;
            }

            if (inFrontMatter) {
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    // 续行
                    currentValue.append(" ").append(line.trim());
                } else if (line.contains(":")) {
                    // 保存上一个key
                    if (currentKey != null && currentValue.length() > 0) {
                        String value = currentValue.toString().trim();
                        if (value.startsWith("[") && value.endsWith("]")) {
                            List<String> items = new ArrayList<>();
                            String arrayContent = value.substring(1, value.length() - 1);
                            for (String item : arrayContent.split(",")) {
                                String trimmed = item.trim();
                                if (!trimmed.isEmpty()) {
                                    items.add(trimmed);
                                }
                            }
                            meta.put(currentKey, items);
                        } else {
                            meta.put(currentKey, value);
                        }
                    }
                    // 解析新key
                    int colonIdx = line.indexOf(":");
                    currentKey = line.substring(0, colonIdx).trim();
                    currentValue = new StringBuilder();
                    String rest = line.substring(colonIdx + 1).trim();
                    if (!rest.isEmpty()) {
                        currentValue.append(rest);
                    }
                }
            }
        }

        return Optional.of(meta);
    }

    /** 获取指定skill声明的tools列表（从SKILL.md元数据解析） */
    public List<String> getSkillTools(String name) {
        Optional<Map<String, Object>> meta = getSkillMetadata(name);
        if (!meta.isPresent()) {
            return Collections.emptyList();
        }
        Object tools = meta.get().get("tools");
        if (tools instanceof List) {
            List<?> list = (List<?>) tools;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
