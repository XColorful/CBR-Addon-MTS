package xiao.cbra.mts.compat.neoforge.init;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import xiao.cbra.mts.CbraMts;
import xiao.cbra.mts.common.VehicleRenderDistance;

/**
 * NeoForge 侧的生命周期接线。
 */
@EventBusSubscriber(modid = CbraMts.MOD_ID)
public class NeoForgeCommonSetup {

    /**
     * 载具的可见范围写在实体类型的 clientTrackingRange 上，只能在实体类型注册完成后改写。
     */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(VehicleRenderDistance::apply);
    }
}
