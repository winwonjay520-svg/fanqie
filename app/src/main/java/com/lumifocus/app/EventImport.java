package com.lumifocus.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** A countdown-only document. Parsing and merging never mutate the current workspace. */
final class EventImport {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_EVENTS = 5000;
    final JSONArray events = new JSONArray();
    private int pinIndex = -1;

    static EventImport read(InputStream in) throws Exception {
        if (in == null) throw new IOException("文件无法打开");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (bytes.size() + read > MAX_BYTES) throw new IOException("文件超过 1 MB");
            bytes.write(buffer, 0, read);
        }
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        JSONTokener reader = new JSONTokener(text.startsWith("\uFEFF") ? text.substring(1) : text) {
            private int depth;
            @Override public Object nextValue() throws JSONException {
                // Android's tokener recursively calls nextValue for nested array/object members.
                if (depth >= 16) throw new IllegalArgumentException("倒数日文件层级过深");
                depth++;
                try { return super.nextValue(); }
                finally { depth--; }
            }
        };
        Object document = reader.nextValue();
        if (!(document instanceof JSONObject) || reader.nextClean() != 0)
            throw new IllegalArgumentException("请选择倒数日 JSON 文件");
        JSONObject root = (JSONObject) document;
        if (!"fanqie-events-v1".equals(root.optString("format")))
            throw new IllegalArgumentException("文件不是番茄倒数日格式");
        JSONArray rows = root.getJSONArray("events");
        if (rows.length() < 1 || rows.length() > MAX_EVENTS)
            throw new IllegalArgumentException("每份文件可包含 1–5000 项倒数日");
        EventImport result = new EventImport();
        for (int i = 0; i < rows.length(); i++) {
            try {
                JSONObject row = rows.getJSONObject(i);
                String title = text(row, "title", null);
                if (title.isEmpty() || title.length() > 40) throw new IllegalArgumentException("名称须为 1–40 字");
                String date = WorkspaceFeatures.date(text(row, "date", null), false);
                String repeat = text(row, "repeat", "none");
                CalendarDates.validateRepeat(repeat);
                String category = text(row, "category", "生活期待");
                if (category.length() > 40) throw new IllegalArgumentException("分类过长");
                String color = text(row, "color", "blue");
                if (!color.equals("blue") && !color.equals("green") && !color.equals("peach"))
                    throw new IllegalArgumentException("无效卡片颜色");
                Object pinned = row.opt("pinned");
                if (pinned != null && !(pinned instanceof Boolean)) throw new IllegalArgumentException("置顶值无效");
                if (Boolean.TRUE.equals(pinned)) {
                    if (result.pinIndex != -1) throw new IllegalArgumentException("只能置顶一项");
                    result.pinIndex = i;
                }
                result.events.put(new JSONObject().put("title", title).put("date", date).put("repeat", repeat)
                        .put("category", category.isEmpty() ? "生活期待" : category).put("color", color));
            } catch (Exception e) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 项倒数日的资料无效，请检查文件", e);
            }
        }
        return result;
    }

    private static String text(JSONObject row, String key, String fallback) {
        Object value = row.opt(key);
        if (value == null && fallback != null) return fallback;
        if (!(value instanceof String)) throw new IllegalArgumentException("无效字段");
        return ((String) value).trim();
    }

    String preview() throws JSONException {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < Math.min(6, events.length()); i++) {
            JSONObject row = events.getJSONObject(i);
            lines.append(row.getString("title")).append("　").append(row.getString("date"));
            if (i == pinIndex) lines.append("（置顶）");
            lines.append('\n');
        }
        if (events.length() > 6) lines.append("……\n");
        return lines.append("\n共 ").append(events.length()).append(" 项，相同记录将自动跳过。").toString();
    }

    static final class Merge {
        final JSONArray events = new JSONArray();
        String pinnedId;
        int added;
        int skipped;

        Merge(String pinnedId) { this.pinnedId = pinnedId; }
    }

    Merge merge(JSONArray existing, String pinnedId) throws JSONException {
        Merge result = new Merge(pinnedId);
        Map<String, String> ids = new HashMap<>();
        for (int i = 0; i < existing.length(); i++) {
            JSONObject row = existing.getJSONObject(i);
            result.events.put(row);
            ids.putIfAbsent(key(row), row.getString("id"));
        }
        for (int i = 0; i < events.length(); i++) {
            JSONObject incoming = events.getJSONObject(i);
            String key = key(incoming);
            String id = ids.get(key);
            if (id == null) {
                if (result.events.length() >= MAX_EVENTS) throw new IllegalArgumentException("倒数日数量已达上限");
                id = UUID.randomUUID().toString();
                result.events.put(new JSONObject(incoming.toString()).put("id", id));
                ids.put(key, id);
                result.added++;
            } else {
                result.skipped++;
            }
            if (i == pinIndex) result.pinnedId = id;
        }
        return result;
    }

    private static String key(JSONObject row) throws JSONException {
        return new JSONArray().put(row.getString("title").trim()).put(row.getString("date"))
                .put(row.optString("repeat", "none")).toString();
    }
}
