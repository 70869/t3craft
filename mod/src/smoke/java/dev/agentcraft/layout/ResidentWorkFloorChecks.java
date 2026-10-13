package dev.agentcraft.layout;

import com.google.gson.JsonParser;
import dev.agentcraft.Cast;
import dev.agentcraft.client.agents.StationAssigner;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.zip.ZipFile;

/** Exercise the actual authored release campus, including historical upstairs desk anchors. */
public final class ResidentWorkFloorChecks {
    public static void main(String[] args) {
        try (var world = new ZipFile(Path.of("../release/world-template.zip").toFile())) {
            var json = JsonParser.parseString(new String(world.getInputStream(world.getEntry("agentcraft-anchors.json")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var layout = Anchors.fromJson(json);
            String original = Anchors.toJson(layout).toString();
            var floor = layout.get("campus_work");
            var positions = new HashSet<String>();
            for (String id : Cast.ids()) {
                var desk = StationAssigner.workDesk(layout, id);
                if (desk == null || floor == null || Math.abs(desk.y() - floor.y()) >= 1) throw new AssertionError(id + " must work on the authored main floor");
                if (!positions.add(desk.pos().toString())) throw new AssertionError("Workers must have distinct positions: " + id);
            }
            if (!original.equals(Anchors.toJson(layout).toString())) throw new AssertionError("Saved authored anchors must remain unchanged");
            System.out.println("PASS authored world: eighteen distinct main-floor work positions; saved anchors unchanged");
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot verify the authored release world", e); }
    }
}
