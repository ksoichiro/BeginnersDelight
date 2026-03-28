#!/usr/bin/env python3
"""Apply Phase 2 VillageManager changes to remaining versions."""
import os

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Version configs: (version, server_access, spawn_method, teleport_style, isEmpty_style)
# server_access: how to get server from player
# spawn_method: how to get spawn pos
# teleport_style: 'set' = Set.of() style, 'simple' = yRot/xRot args, 'field' = yRot/xRot fields
# isEmpty_style: 'isEmpty' or 'isPresent'
VERSIONS = [
    ("1.21.6", "player.getServer()", "overworld.getSharedSpawnPos()", "set", "isEmpty"),
    ("1.21.5", "player.getServer()", "overworld.getSharedSpawnPos()", "set", "isEmpty"),
    ("1.21.4", "player.server", "overworld.getSharedSpawnPos()", "set", "isEmpty"),
    ("1.21.3", "player.server", "overworld.getSharedSpawnPos()", "set", "isEmpty"),
    ("1.21.1", "player.server", "overworld.getSharedSpawnPos()", "set", "isEmpty"),
    ("1.20.1", "player.server", "overworld.getSharedSpawnPos()", "simple", "isEmpty"),
    ("1.19.2", "player.server", "overworld.getSharedSpawnPos()", "simple", "isEmpty"),
    ("1.18.2", "player.server", "overworld.getSharedSpawnPos()", "simple", "isEmpty"),
    ("1.17.1", "player.server", "overworld.getSharedSpawnPos()", "simple", "isEmpty"),
    ("1.16.5", "player.server", "overworld.getSharedSpawnPos()", "field", "isPresent"),
]

for (ver, server_access, spawn_method, tp_style, empty_style) in VERSIONS:
    path = os.path.join(BASE, f"common/{ver}/src/main/java/com/beginnersdelight/village/VillageManager.java")
    with open(path, 'r') as f:
        content = f.read()

    # 1. Add StarterHouseData import
    content = content.replace(
        "import com.beginnersdelight.BeginnersDelight;\nimport net.minecraft.core.BlockPos;",
        "import com.beginnersdelight.BeginnersDelight;\nimport com.beginnersdelight.worldgen.StarterHouseData;\nimport net.minecraft.core.BlockPos;",
        1
    )

    # 2. Replace onPlayerJoin - remove returning player teleport, add StarterHouseData check
    # Find the hasHouse block and replace it
    old_has_house = f"""        if (data.hasHouse(player.getUUID())) {{
            // Returning player — teleport to existing house
            GridPos gridPos = data.getPlayerHouse(player.getUUID());
            BlockPos housePos = data.getHousePosition(gridPos);
            if (housePos != null) {{"""

    # Find where this block ends (after the return;) and replace whole section
    idx = content.find(old_has_house)
    if idx == -1:
        print(f"WARNING: Could not find hasHouse block in {ver}")
        continue

    # Find the matching closing pattern
    end_pattern = """            return;
        }

        // New player — assign a house
        assignHouse(overworld, player, data);"""

    end_idx = content.find(end_pattern, idx)
    if end_idx == -1:
        print(f"WARNING: Could not find end pattern in {ver}")
        continue

    end_idx += len(end_pattern)

    new_section = f"""        if (data.hasHouse(player.getUUID())) return;
        StarterHouseData starterData = StarterHouseData.get(overworld);
        if (starterData.hasBeenTeleported(player.getUUID()) && starterData.getSpawnPos() != null) {{
            registerStarterHouseAsVillageHouse(overworld, player, data, starterData.getSpawnPos());
            return;
        }}
        assignHouse(overworld, player, data);"""

    content = content[:idx] + new_section + content[end_idx:]

    # 3. Add registerStarterHouseAsVillageHouse before initializeGrid
    content = content.replace(
        "    private static void initializeGrid(ServerLevel overworld, VillageData data) {",
        f"""    private static void registerStarterHouseAsVillageHouse(ServerLevel overworld, ServerPlayer player, VillageData data, BlockPos starterHousePos) {{
        if (data.getCenterPos() == null) initializeGrid(overworld, data);
        GridPos centerGrid = new GridPos(0, 0);
        data.setPlotState(centerGrid, PlotState.OCCUPIED); data.setPlayerHouse(player.getUUID(), centerGrid);
        data.setHousePosition(centerGrid, starterHousePos); data.setDoorPosition(centerGrid, starterHousePos);
        data.incrementHouseCountSinceLastDecoration();
        BeginnersDelight.LOGGER.info("Registered starter house as village house for player {{}}", player.getName().getString());
    }}
    private static void initializeGrid(ServerLevel overworld, VillageData data) {{""",
        1
    )

    # 4. Add decoration trigger + tryPlaceDecoration at end
    old_end = """        BeginnersDelight.LOGGER.info("Assigned village house to player {} at grid {}",
                player.getName().getString(), gridPos);
    }
}"""

    empty_check = 'candidate.isEmpty()' if empty_style == 'isEmpty' else '!candidate.isPresent()'
    result_empty = 'result.isEmpty()' if empty_style == 'isEmpty' else '!result.isPresent()'

    new_end = f"""        BeginnersDelight.LOGGER.info("Assigned village house to player {{}} at grid {{}}",
                player.getName().getString(), gridPos);
        data.incrementHouseCountSinceLastDecoration();
        if (data.getHouseCountSinceLastDecoration() >= 2) tryPlaceDecoration(overworld, data);
    }}
    private static void tryPlaceDecoration(ServerLevel overworld, VillageData data) {{
        if (data.getCenterPos() == null) return;
        VillageGrid grid = new VillageGrid(data, config);
        String structureName = data.getDecorationCount() == 0 ? "village_well" : VillageHouseGenerator.selectRandomDecoration(overworld.getRandom());
        for (int attempt = 0; attempt < 10; attempt++) {{
            Optional<GridPos> candidate = grid.findNextAvailablePlot();
            if ({empty_check}) {{ BeginnersDelight.LOGGER.warn("No available plots for decoration"); return; }}
            GridPos candidatePos = candidate.get(); BlockPos worldPos = grid.gridToWorld(candidatePos);
            if (!VillageHouseGenerator.isSuitable(overworld, worldPos, config.getMaxHeightDifference())) {{ data.setPlotState(candidatePos, PlotState.UNSUITABLE); continue; }}
            Optional<VillageHouseGenerator.PlacementResult> result = VillageHouseGenerator.placeDecoration(overworld, worldPos, structureName);
            if ({result_empty}) {{ data.setPlotState(candidatePos, PlotState.UNSUITABLE); continue; }}
            VillageHouseGenerator.PlacementResult placement = result.get();
            data.setPlotState(candidatePos, PlotState.DECORATION); data.setDoorPosition(candidatePos, placement.doorFrontPos());
            data.incrementDecorationCount(); data.setHouseCountSinceLastDecoration(0);
            if (config.isGeneratePaths()) {{
                Optional<GridPos> nearestOpt = grid.findNearestOccupiedPlot(candidatePos);
                if (nearestOpt.isPresent()) {{ BlockPos nd = data.getDoorPosition(nearestOpt.get()); if (nd != null) VillagePathGenerator.generatePath(overworld, placement.doorFrontPos(), nd); }}
                else VillagePathGenerator.generatePath(overworld, placement.doorFrontPos(), data.getCenterPos());
            }}
            BeginnersDelight.LOGGER.info("Placed decoration '{{}}' at grid {{}}", structureName, candidatePos); return;
        }}
        BeginnersDelight.LOGGER.warn("Failed to place decoration after 10 attempts");
    }}
}}"""

    content = content.replace(old_end, new_end, 1)

    with open(path, 'w') as f:
        f.write(content)
    print(f"Updated VillageManager for {ver}")

print("Done!")
