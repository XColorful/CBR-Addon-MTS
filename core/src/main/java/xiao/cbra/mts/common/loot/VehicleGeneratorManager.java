package xiao.cbra.mts.common.loot;

import org.jetbrains.annotations.Nullable;
import xiao.battleroyale.api.event.CustomEventType;
import xiao.battleroyale.api.event.ICustomEvent;
import xiao.battleroyale.api.event.ICustomEventHandler;
import xiao.battleroyale.api.event.loot.generate.CustomGenerateEvent;
import xiao.cbra.mts.common.loot.data.VehicleDataProtocol;

public class VehicleGeneratorManager implements ICustomEventHandler {

    public static class VehicleGeneratorManagerHolder {
        private static final VehicleGeneratorManager INSTANCE = new VehicleGeneratorManager();
    }

    public static VehicleGeneratorManager get() {
        return VehicleGeneratorManagerHolder.INSTANCE;
    }

    protected VehicleGeneratorManager() {}

    @Override
    public String getEventHandlerName() {
        return this.getClass().getName();
    }
    @Override
    public void handleEvent(CustomEventType eventType, ICustomEvent event) {
        if (eventType == CustomEventType.CUSTOM_GENERATE_EVENT) {
            onCustomGenerate((CustomGenerateEvent<?>) event);
        } else {
            onReceiveWrongEvent(eventType);
        }
    }

    private void onCustomGenerate(CustomGenerateEvent<?> event) {
        @Nullable VehicleDataProtocol protocol = VehicleDataProtocol.getConfigFromProtocol(event.getProtocol(), event.getJsonTag());
        if (protocol == null) return;

        VehicleGenerator.generateVehicle(event.getLootContext(), event.getTarget(), event.getLootData(),
                protocol);

        event.setCanceled(true);
    }
}
