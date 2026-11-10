package com.star4droid.star2d.suda;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Running generations: keeps every started job (also after its dialog is closed), polls GET /jobs/{id}
 * (every 3 s for images, every 20 s for long 3D jobs, or on demand) and tells listeners. Survives app restarts:
 * the list is stored in the account prefs.
 */
public final class Jobs {
    private Jobs() {}

    public static final class Job {
        public String id, op, title, status = "queued", error = "";
        public int progress;
        public double credits = -1;
        public long created;
        public JsonArray outputs = new JsonArray();
        /** Non-file result of a job (rig check: riggable, rig_type); may be null. */
        public JsonObject result;
        /** Free text tag: "ws:<workspace>:<node>" or "quick". */
        public String tag = "";
        public boolean finished() { return "success".equals(status) || "failed".equals(status) || "cancelled".equals(status); }
        public boolean ok() { return "success".equals(status); }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id); o.addProperty("op", op); o.addProperty("title", title); o.addProperty("status", status);
            o.addProperty("error", error); o.addProperty("progress", progress); o.addProperty("credits", credits);
            o.addProperty("created", created); o.add("outputs", outputs); o.addProperty("tag", tag); if (result != null) o.add("result", result);
            return o;
        }

        static Job from(JsonObject o) {
            Job j = new Job();
            j.id = Suda3D.str(o, "id"); j.op = Suda3D.str(o, "op"); j.title = Suda3D.str(o, "title"); j.status = Suda3D.str(o, "status");
            j.error = Suda3D.str(o, "error"); j.progress = (int) Suda3D.num(o, "progress", 0); j.credits = Suda3D.num(o, "credits", -1);
            j.created = (long) Suda3D.num(o, "created", 0); j.outputs = Suda3D.arr(o, "outputs"); j.tag = Suda3D.str(o, "tag"); j.result = Suda3D.obj(o, "result");
            return j;
        }

        /** First output of a kind (model / image), or null. */
        public JsonObject output(String kind) {
            for (JsonElement e : outputs) if (e.isJsonObject() && (kind == null || kind.equals(Suda3D.str(e.getAsJsonObject(), "kind")))) return e.getAsJsonObject();
            return null;
        }
    }

    public interface Listener { void onJob(Job j); }

    private static final Map<String, Job> JOBS = new LinkedHashMap<>();
    private static final Map<String, ScheduledFuture<?>> TIMERS = new LinkedHashMap<>();
    private static final CopyOnWriteArrayList<Listener> L = new CopyOnWriteArrayList<>();
    private static final ScheduledExecutorService POLL = Executors.newSingleThreadScheduledExecutor();
    private static Context app;
    private static final String PREF = "running_jobs";

    public static synchronized void init(Context c) {
        if (app != null) return;
        app = c.getApplicationContext();
        try {
            String s = Suda3D.prefs(app).getString(PREF, "");
            if (!s.isEmpty()) for (JsonElement e : JsonParser.parseString(s).getAsJsonArray()) {
                Job j = Job.from(e.getAsJsonObject());
                JOBS.put(j.id, j);
                if (!j.finished()) schedule(j, 2);
            }
        } catch (Exception ignored) {}
    }

    public static void listen(Listener l) { L.addIfAbsent(l); }
    public static void unlisten(Listener l) { L.remove(l); }

    public static synchronized List<Job> all() { return new ArrayList<>(JOBS.values()); }
    public static synchronized Job get(String id) { return JOBS.get(id); }

    /** Remove finished jobs from the running list. */
    public static synchronized void clearFinished() {
        JOBS.values().removeIf(Job::finished);
        save();
    }

    /** Starts tracking a job created by POST /jobs. */
    public static synchronized Job track(Context c, JsonObject created, String op, String title, String tag) {
        init(c);
        Job j = new Job();
        j.id = Suda3D.str(created, "job_id");
        j.op = op;
        j.title = title;
        j.tag = tag == null ? "" : tag;
        j.status = Suda3D.str(created, "status").isEmpty() ? "queued" : Suda3D.str(created, "status");
        JsonObject cost = Suda3D.obj(created, "cost");
        j.credits = Suda3D.num(cost, "credits", -1);
        j.created = System.currentTimeMillis();
        JOBS.put(j.id, j);
        save();
        schedule(j, firstDelay(j));
        notifyAll(j);
        return j;
    }

    /** Make sure a known job id is tracked (workspace reopened after the running list was cleared). */
    public static synchronized Job resume(Context c, String id, String op, String title, String tag) {
        init(c);
        Job j = JOBS.get(id);
        if (j != null) return j;
        j = new Job();
        j.id = id; j.op = op; j.title = title; j.tag = tag == null ? "" : tag; j.created = System.currentTimeMillis();
        JOBS.put(id, j);
        save();
        schedule(j, 1);
        return j;
    }

    static long firstDelay(Job j) { Ops.Op op = Ops.get(j.op); return op != null && op.makesModel() ? 20 : 3; }
    static long interval(Job j) { Ops.Op op = Ops.get(j.op); return op != null && op.makesModel() ? 20 : 3; }

    private static synchronized void schedule(Job j, long delaySec) {
        ScheduledFuture<?> old = TIMERS.remove(j.id);
        if (old != null) old.cancel(false);
        TIMERS.put(j.id, POLL.schedule(() -> poll(j.id), delaySec, TimeUnit.SECONDS));
    }

    /** Manual "Refresh": poll now. */
    public static void refresh(String id) {
        Job j = get(id);
        if (j != null) schedule(j, 0);
    }

    private static void poll(String id) {
        Job j = get(id);
        if (j == null || app == null) return;
        try {
            JsonObject r = Suda3D.job(app, id);
            synchronized (Jobs.class) {
                String st = Suda3D.str(r, "status");
                if (!st.isEmpty()) j.status = st;
                j.progress = (int) Suda3D.num(r, "progress", j.progress);
                JsonObject cost = Suda3D.obj(r, "cost");
                if (cost != null) j.credits = Suda3D.num(cost, "credits", j.credits);
                j.outputs = Suda3D.arr(r, "outputs");
                JsonObject res = Suda3D.obj(r, "result");
                if (res != null) j.result = res;
                JsonElement err = r.get("error");
                if (err != null && !err.isJsonNull()) j.error = err.isJsonObject() ? Suda3D.str(err.getAsJsonObject(), "message") : err.getAsString();
                save();
            }
        } catch (Exception e) {
            j.error = Suda3D.message(e);
        }
        notifyAll(j);
        if (!j.finished()) schedule(j, interval(j));
        else {
            TIMERS.remove(id);
            Suda3D.IO.execute(() -> { try { Suda3D.me(app); } catch (Exception ignored) {} });
        }
    }

    public static void cancel(String id) {
        Suda3D.IO.execute(() -> {
            try { Suda3D.cancelJob(app, id); } catch (Exception ignored) {}
            refresh(id);
        });
    }

    static void notifyAll(Job j) { for (Listener l : L) Ui.ui(() -> l.onJob(j)); }

    private static synchronized void save() {
        if (app == null) return;
        JsonArray a = new JsonArray();
        for (Job j : JOBS.values()) a.add(j.toJson());
        Suda3D.prefs(app).edit().putString(PREF, a.toString()).apply();
    }
}
