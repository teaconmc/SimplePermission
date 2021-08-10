package org.teacon.permission;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.SPlayerListItemPacket;
import net.minecraft.network.play.server.SPlayerListItemPacket.AddPlayerData;
import net.minecraft.server.management.PlayerList;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

public final class VanillaPacketUtils {

    private static final Logger LOGGER = LogManager.getLogger("Nickname");
    private static final Marker MARKER = MarkerManager.getMarker("Packet");

    private static final Field DISPLAY_NAME;

    static {
        DISPLAY_NAME = ObfuscationReflectionHelper.findField(SPlayerListItemPacket.class, "field_179769_b");
    }

    @SuppressWarnings("unchecked")
    public static SPlayerListItemPacket displayNameUpdatePacketFor(ServerPlayerEntity player) {
        final SPlayerListItemPacket packet = new SPlayerListItemPacket(SPlayerListItemPacket.Action.UPDATE_DISPLAY_NAME, Collections.emptyList());
        try {
            List<SPlayerListItemPacket.AddPlayerData> playerData = (List<AddPlayerData>) DISPLAY_NAME.get(packet);
            playerData.add(packet.new AddPlayerData(player.getGameProfile(), player.latency, player.gameMode.getGameModeForPlayer(), player.getDisplayName()));
        } catch (Exception e) {
            LOGGER.warn(MARKER, "Failed to construct PlayerListItemPacket, nickname will be out of sync. Check debug.log for more information.");
            LOGGER.debug(MARKER, "Details: ", e);
        }
        return packet;
    }

    @SuppressWarnings("unchecked")
    public static SPlayerListItemPacket displayNameUpdatePacketForAll(PlayerList playerList) {
        final SPlayerListItemPacket packet = new SPlayerListItemPacket(SPlayerListItemPacket.Action.UPDATE_DISPLAY_NAME, Collections.emptyList());
        try {
            for (ServerPlayerEntity player : playerList.getPlayers()) {
                List<SPlayerListItemPacket.AddPlayerData> playerData = (List<AddPlayerData>) DISPLAY_NAME.get(packet);
                playerData.add(packet.new AddPlayerData(player.getGameProfile(), player.latency, player.gameMode.getGameModeForPlayer(), player.getDisplayName()));
            }
        } catch (Exception e) {
            LOGGER.warn(MARKER, "Failed to construct PlayerListItemPacket, nickname will be out of sync. Check debug.log for more information.");
            LOGGER.debug(MARKER, "Details: ", e);
        }
        return packet;
    }
}