package com.lumifocus.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import org.json.JSONArray;
import org.json.JSONObject;

final class EventImportChecks {
    private static EventImport file(JSONObject... events) throws Exception {
        JSONArray rows = new JSONArray();
        for (JSONObject e : events) rows.put(e);
        String json = new JSONObject().put("format", "fanqie-events-v1").put("events", rows).toString();
        return EventImport.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static JSONObject row(String title, String date) throws Exception {
        return new JSONObject().put("title", title).put("date", date);
    }

    private static void check(boolean pass, String label) {
        if (!pass) throw new AssertionError(label);
    }

    static String run(Context app) throws Exception {
        // This test can also run on devices which already contain a real workspace.
        String prefsName = "qa_event_import_" + UUID.randomUUID();
        SharedPreferences isolated = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        SharedPreferences appearance = app.getSharedPreferences(prefsName + "_appearance", Context.MODE_PRIVATE);
        Context context = new ContextWrapper(app) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return "lumi_v1".equals(name) ? isolated : appearance;
            }
        };
        try {
            JSONObject original = row("示例 A", "2032-04-11").put("id", "old").put("category", "保留原分类")
                    .put("color", "peach").put("extra", 3);
            JSONArray history = new JSONArray().put(new JSONObject().put("seconds", 21));
            JSONObject seed = new JSONObject().put("events", new JSONArray().put(original))
                    .put("sessions", history).put("timer", new JSONObject().put("status", "paused")
                            .put("duration", 1500).put("elapsed", 15000).put("mode", "focus"))
                    .put("_eventsRevision", 2).put("_revision", 8);
            check(isolated.edit().putString("data", seed.toString()).commit(), "seed");
            Store store = new Store(context);
            JSONObject another = row("示例 B", "2032-09-03").put("repeat", "yearly");
            EventImport input = file(row("示例 A", "2032-04-11").put("pinned", true), another, another);
            EventImport.Merge result = store.importEvents(input);
            check(result.added == 1 && result.skipped == 2 && "old".equals(result.pinnedId), "duplicate and pin");
            JSONObject persisted = new JSONObject(isolated.getString("data", ""));
            check(persisted.getJSONArray("events").length() == 2, "merged list");
            check(persisted.getJSONArray("events").getJSONObject(0).toString().equals(original.toString()), "existing metadata");
            check(persisted.getJSONArray("sessions").toString().equals(history.toString())
                    && persisted.getJSONObject("timer").toString().equals(seed.getJSONObject("timer").toString()), "timer and history");
            check(persisted.getInt("_eventsRevision") == 3 && persisted.getInt("_revision") == 9, "single commit");
            String after = isolated.getString("data", "");
            EventImport.Merge retry = new Store(context).importEvents(input);
            check(retry.added == 0 && retry.skipped == 3 && isolated.getString("data", "").equals(after), "restart and retry");
            try {
                store.importEvents(file(another, row("无效示例", "2032-02-30")));
                throw new AssertionError("invalid file accepted");
            } catch (IllegalArgumentException expected) {
                check(after.equals(isolated.getString("data", "")), "invalid batch modified storage");
            }
            StringBuilder deep = new StringBuilder();
            for (int i = 0; i < 500; i++) deep.append('[');
            for (int i = 0; i < 500; i++) deep.append(']');
            try {
                EventImport.read(new ByteArrayInputStream(deep.toString().getBytes(StandardCharsets.UTF_8)));
                throw new AssertionError("deep input accepted");
            } catch (IllegalArgumentException expected) {
                check(after.equals(isolated.getString("data", "")), "deep input modified storage");
            }
            EventImport parallel = file(row("示例 C", "2033-05-04").put("repeat", "weekly"));
            FutureTask<EventImport.Merge> task = new FutureTask<>(() -> store.importEvents(parallel));
            new Thread(task).start();
            check(store.importEvents(parallel).added + task.get().added == 1, "concurrent retry");
            check("old".equals(store.importEvents(parallel).pinnedId), "unmarked import keeps pin");
            String otherId = result.events.getJSONObject(1).getString("id");
            JSONObject pinned = new JSONObject(store.command(new JSONObject().put("action", "pinEvent").put("id", otherId).toString()));
            check(otherId.equals(pinned.getString("pinnedEventId")), "pin command");
            JSONObject unpinned = new JSONObject(store.command(new JSONObject().put("action","eventDelete").put("id",otherId).toString()));
            check(unpinned.getString("pinnedEventId").isEmpty(), "delete clears pin");
            return "PASS Atomic countdown import, metadata preservation, retry after restart, validation, concurrent merge and pinning\n";
        } finally {
            app.deleteSharedPreferences(prefsName);
            app.deleteSharedPreferences(prefsName + "_appearance");
        }
    }
}
