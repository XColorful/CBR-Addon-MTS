package xiao.cbra.mts;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import xiao.battleroyale.BattleRoyale;
import xiao.battleroyale.api.common.McSide;
import xiao.cbra.mts.config.CbraMtsConfig;
import xiao.cbra.mts.event.custom.CustomEventHandler;

import java.nio.file.Path;

public class CbraMts {
    public static final String MOD_ID = "cbramts";
    public static final Logger LOGGER = LogUtils.getLogger();

    protected static boolean initialized;

    /**
     * 配置文件名，位于平台的 config 目录下
     */
    public static final String FILE_NAME = MOD_ID + ".json";

    /**
     * 游戏根目录，用于解析配置中的相对路径
     */
    private static Path gameDirectory;

    /**
     * 模组配置文件的绝对路径
     */
    private static Path configFile;

    public static void init(McSide mcSide,
                            Path gameDirectory, Path configDirectory) {
        if (initialized) return;

        CbraMts.gameDirectory = gameDirectory;
        CbraMts.configFile = configDirectory.resolve(FILE_NAME);

        CbraMtsConfig.load(CbraMts.configFile);

        CustomEventHandler.registerAll(BattleRoyale.getEventRegister());

        initialized = true;
    }

    /**
     * 重新从磁盘读取配置
     */
    public static void reloadConfig() {
        if (configFile == null) return;
        CbraMtsConfig.load(configFile);
    }

    /**
     * @return 游戏根目录
     */
    public static Path gameDirectory() {
        return gameDirectory;
    }

    /**
     * @return 模组配置文件的绝对路径
     */
    public static Path configFile() {
        return configFile;
    }
}
