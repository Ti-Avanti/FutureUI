package gg.fotia.futureui.integration;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.bukkit.entity.Player;

/** 读取协议层提供的客户端版本；未知版本不推断为已验证的着色器版本。 */
public final class ClientCapabilities {
    private ClientCapabilities(){}
    public static int protocol(Player player){ClientVersion version=PacketEvents.getAPI().getPlayerManager().getClientVersion(player);return version==null||version==ClientVersion.UNKNOWN?-1:version.getProtocolVersion();}
    public static boolean supportsDialog(Player player){ClientVersion version=PacketEvents.getAPI().getPlayerManager().getClientVersion(player);return version!=null&&version!=ClientVersion.UNKNOWN&&version.isNewerThanOrEquals(ClientVersion.V_1_21_6);}
}
