package xiao.cbra.mts.compat.forge.init;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import xiao.cbra.mts.CbraMts;
import xiao.cbra.mts.common.VehicleRenderDistance;

/**
 * Forge 侧的生命周期接线。
 */
@Mod.EventBusSubscriber(modid = CbraMts.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeCommonSetup {

    private ForgeCommonSetup() {
    }

    /**
     * 载具的可见范围写在实体类型的 clientTrackingRange 上，只能在实体类型注册完成后改写。
     */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(VehicleRenderDistance::apply);
    }
}
