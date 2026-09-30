package xiao.cbra.mts;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import xiao.battleroyale.BattleRoyale;
import xiao.battleroyale.api.common.McSide;
import xiao.cbra.mts.event.custom.CustomEventHandler;

public class CbraMts {
    public static final String MOD_ID = "cbramts";
    public static final Logger LOGGER = LogUtils.getLogger();

    protected static boolean initialized;

    public static void init(McSide mcSide) {
        if (initialized) return;

        CustomEventHandler.registerAll(BattleRoyale.getEventRegister());

        initialized = true;
    }
}
