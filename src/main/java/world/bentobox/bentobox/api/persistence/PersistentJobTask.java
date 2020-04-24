package world.bentobox.bentobox.api.persistence;

import org.bukkit.event.Event;

public abstract class PersistentJobTask {

    public abstract <T extends Event> void onCompletion(T event);
}
