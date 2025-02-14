package world.bentobox.bentobox.api.commands.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.util.Vector;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.commands.ConfirmableCommand;
import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent.Reason;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;

public class AdminDeleteCommand extends ConfirmableCommand {

    public AdminDeleteCommand(CompositeCommand parent) {
        super(parent, "delete");
    }

    @Override
    public void setup() {
        setPermission("admin.delete");
        setParametersHelp("commands.admin.delete.parameters");
        setDescription("commands.admin.delete.description");
    }

    @Override
    public boolean canExecute(User user, String label, List<String> args) {
        if (args.size() != 1) {
            showHelp(this, user);
            return false;
        }
        // Get target
        UUID targetUUID = Util.getUUID(args.get(0));
        if (targetUUID == null) {
            user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
            return false;
        }
        UUID owner = getIslands().getOwner(getWorld(), targetUUID);
        if (owner == null) {
            user.sendMessage("general.errors.player-has-no-island");
            return false;
        }

        // Team members should be kicked before deleting otherwise the whole team will become weird
        if (getIslands().inTeam(getWorld(), targetUUID) && owner.equals(targetUUID)) {
            user.sendMessage("commands.admin.delete.cannot-delete-owner");
            return false;
        }
        return true;
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        // Get target
        UUID targetUUID = getPlayers().getUUID(args.get(0));
        // Confirm
        askConfirmation(user, () -> deletePlayer(user, targetUUID));
        return true;
    }

    private void deletePlayer(User user, UUID targetUUID) {
        // Delete player and island
        // Get the target's island
        Island oldIsland = getIslands().getIsland(getWorld(), targetUUID);
        Vector vector = null;
        if (oldIsland != null) {
            // Fire island preclear event
            IslandEvent.builder()
            .involvedPlayer(user.getUniqueId())
            .reason(Reason.PRECLEAR)
            .island(oldIsland)
            .oldIsland(oldIsland)
            .location(oldIsland.getCenter())
            .build();
            // Check if player is online and on the island
            User target = User.getInstance(targetUUID);
            // Remove them from this island (it still exists and will be deleted later)
            getIslands().removePlayer(getWorld(), targetUUID);
            if (target.isPlayer() && target.isOnline()) {
                cleanUp(user, target);
            }
            vector = oldIsland.getCenter().toVector();
            getIslands().deleteIsland(oldIsland, true, targetUUID);
        }
        if (vector == null) {
            user.sendMessage("general.success");
        } else {
            user.sendMessage("commands.admin.delete.deleted-island", TextVariables.XYZ, Util.xyz(vector));
        }
    }

    private void cleanUp(User user, User target) {
        // Remove money inventory etc.
        if (getIWM().isOnLeaveResetEnderChest(getWorld())) {
            target.getPlayer().getEnderChest().clear();
        }
        if (getIWM().isOnLeaveResetInventory(getWorld())) {
            target.getPlayer().getInventory().clear();
        }
        if (getSettings().isUseEconomy() && getIWM().isOnLeaveResetMoney(getWorld())) {
            getPlugin().getVault().ifPresent(vault -> vault.withdraw(target, vault.getBalance(target)));
        }
        // Reset the health
        if (getIWM().isOnLeaveResetHealth(getWorld())) {
            Util.resetHealth(target.getPlayer());
        }

        // Reset the hunger
        if (getIWM().isOnLeaveResetHunger(getWorld())) {
            target.getPlayer().setFoodLevel(20);
        }

        // Reset the XP
        if (getIWM().isOnLeaveResetXP(getWorld())) {
            target.getPlayer().setTotalExperience(0);
        }

        // Execute commands when leaving
        Util.runCommands(target, getIWM().getOnLeaveCommands(getWorld()), "leave");
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        String lastArg = !args.isEmpty() ? args.get(args.size()-1) : "";
        if (args.isEmpty()) {
            // Don't show every player on the server. Require at least the first letter
            return Optional.empty();
        }
        List<String> options = new ArrayList<>(Util.getOnlinePlayerList(user));
        return Optional.of(Util.tabLimit(options, lastArg));
    }
}
