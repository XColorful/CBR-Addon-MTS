package xiao.cbra.mts.compat.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import xiao.battleroyale.api.common.McSide;
import xiao.cbra.mts.CbraMts;

@Mod(CbraMts.MOD_ID)
public class CbraMtsForge {

    public CbraMtsForge() {
        Dist dist = FMLLoader.getDist();
        McSide mcSide = dist.isClient() ? McSide.CLIENT : McSide.DEDICATED_SERVER;

        CbraMts.init(mcSide,
                FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get());
    }
}
