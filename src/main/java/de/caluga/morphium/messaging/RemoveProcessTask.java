package de.caluga.morphium.messaging;

import de.caluga.morphium.driver.MorphiumId;

import java.util.List;

public class RemoveProcessTask implements Runnable {
    @SuppressWarnings("CanBeFinal")
    List<MorphiumId> processing;
    @SuppressWarnings("CanBeFinal")
    MorphiumId toRemove;

    public RemoveProcessTask(List<MorphiumId> processing, MorphiumId toRemove) {
        this.processing = processing;
        this.toRemove = toRemove;
    }

    @Override
    public void run() {
        processing.remove(toRemove);
    }
}
