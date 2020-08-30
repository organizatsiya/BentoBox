package world.bentobox.bentobox.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import world.bentobox.bentobox.BentoBox;

/**
 * Manages Bungecord related functions
 * @author tastybento
 *
 */
public class BungeeManager implements PluginMessageListener {

    private final ByteArrayDataOutput out;
    private final List<CompletableFuture<List<String>>> pendingGetAllPlayers = new ArrayList<>();

    /**
     *
     */
    public BungeeManager(BentoBox plugin) {
        // Register with proxy
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);
        out = ByteStreams.newDataOutput();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();
        // PlayerList
        if (subchannel.equals("PlayerList")) {
            String server = in.readUTF(); // The name of the server you got the player list of, as given in args.
            if (server.equals("ALL")) {
                List<String> playerList = Arrays.asList(in.readUTF().split(", "));
                pendingGetAllPlayers.forEach(c -> c.complete(playerList));
            }
            pendingGetAllPlayers.clear();
        }
    }

    public CompletableFuture<List<String>> getAllPlayers() {
        out.writeUTF("PlayerList");
        out.writeUTF("ALL");
        CompletableFuture<List<String>> result = new CompletableFuture<>();
        pendingGetAllPlayers.add(result);
        return result;
    }
}

