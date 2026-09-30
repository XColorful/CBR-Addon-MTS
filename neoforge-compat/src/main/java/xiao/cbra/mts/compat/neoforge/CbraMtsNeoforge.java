package xiao.cbra.mts.compat.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import xiao.battleroyale.api.common.McSide;
import xiao.cbra.mts.CbraMts;

@Mod(CbraMts.MOD_ID)
public class CbraMtsNeoforge {

    public CbraMtsNeoforge() {
        Dist dist = FMLLoader.getCurrent().getDist();
        McSide mcSide = dist.isClient() ? McSide.CLIENT : McSide.DEDICATED_SERVER;

        CbraMts.init(mcSide);
    }
}
