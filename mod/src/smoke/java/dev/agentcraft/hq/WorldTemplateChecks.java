package dev.agentcraft.hq;

import net.minecraft.nbt.*;

final class WorldTemplateChecks {
    static void run() {
        CompoundTag root = new CompoundTag(), data = new CompoundTag(), block = new CompoundTag();
        data.putString("Player", "private fixture player"); data.putString("LevelName", "AgentCraft HQ");
        block.putString("id", "minecraft:sign"); block.putString("binding", "marlow");
        block.putString("Owner", "private fixture owner"); block.putString("profile", "private fixture profile");
        block.putString("Items", "private fixture inventory"); block.putString("minecraft:written_book_content", "private fixture book");
        var text = new CompoundTag(); text.putString("message", "T3 campus"); block.put("front_text", text);
        var blocks = new ListTag(); blocks.add(block); data.put("block_entities", blocks); root.put("Data", data);
        WorldTemplateExport.scrub(root);
        if (data.contains("Player") || block.contains("Owner") || block.contains("profile") || block.contains("Items")
            || block.contains("minecraft:written_book_content") || !block.getStringOr("binding", "").equals("marlow")
            || !text.getStringOr("message", "").equals("T3 campus")) throw new AssertionError("Template must remove identities/inventory but preserve authored signs and stations");
        for (String path : new String[]{"players/private.dat", "config/t3craft.json", "level.dat_old", "session.lock", "data/minecraft/scoreboard.dat", "../level.dat", "dimensions/minecraft/overworld/entities/r.0.0.mca"})
            if (WorldTemplateExport.allowed(path)) throw new AssertionError("Private template path: " + path);
        System.out.println("PASS clean world template: private player/profile/inventory removal; authored signs/stations retained; private paths excluded");
    }
}
