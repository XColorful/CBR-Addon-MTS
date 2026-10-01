package xiao.cbra.mts.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xiao.battleroyale.util.JsonUtils;
import xiao.cbra.mts.CbraMts;
import xiao.cbra.mts.api.config.CbraMtsConfigTag;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CBR Addon: Immersive Vehicles 的运行时配置。
 * <p>
 * 配置存放在平台的 config 目录下（{@link CbraMts#FILE_NAME}）。
 * 读取时字段缺失用默认值、多余字段忽略，加载后按固定顺序回写，
 * 保证缺省的键也会出现在文件里供使用者编辑。
 */
public final class CbraMtsConfig {

    // --------默认值--------

    private static final double DEFAULT_VEHICLE_HEALTH_SCALE = 1.0D;
    private static final boolean DEFAULT_ALL_PARTS_ATTACKABLE = false;
    private static final boolean DEFAULT_MODIFY_RENDER_DISTANCE = true;
    private static final int DEFAULT_RENDER_DISTANCE = 32;
    private static final boolean DEFAULT_MODIFY_VEHICLE_SOUND_DISTANCE = true;
    private static final int DEFAULT_VEHICLE_SOUND_DISTANCE = 20;
    private static final double DEFAULT_VEHICLE_CRASH_DESTROY_SPEED = 150.0D;
    private static final double DEFAULT_VEHICLE_DESTRUCTION_DAMAGE_SCALE = 0.65D;

    /**
     * 生命上限缩放比例下限，0 表示把上限压到最低值 1
     */
    private static final double MIN_VEHICLE_HEALTH_SCALE = 0.0D;

    /**
     * 可见范围下限（区块）
     */
    private static final int MIN_RENDER_DISTANCE = 1;

    /**
     * 音效传播距离下限（区块）
     */
    private static final int MIN_VEHICLE_SOUND_DISTANCE = 1;

    /**
     * 摧毁速度下限（km/h），0 表示不提高
     */
    private static final double MIN_VEHICLE_CRASH_DESTROY_SPEED = 0.0D;

    /**
     * 碰撞伤害系数下限
     */
    private static final double MIN_VEHICLE_DESTRUCTION_DAMAGE_SCALE = 0.0D;

    // --------配置项--------

    /**
     * 全载具生命上限的缩放比例。载具包原本的上限为 150 时，设为 10.0 后为 1500。
     * <p>
     * 同时作用于载具本体和部件：部件有自己的血量，且会按 forwardsDamageMultiplier 把伤害转发给车体，
     * 只改车体会让两者对不上。值为 0 或负数的定义表示无限血量，不受本项影响。
     */
    public static double vehicleHealthScale = DEFAULT_VEHICLE_HEALTH_SCALE;

    /**
     * 是否让全部部件的碰撞盒都带上 {@code ATTACK} 类型。
     * <p>
     * MTS 默认只有标注了 ATTACK 的碰撞盒才会在外部伤害路径里出伤，
     * 未标注的盒会被子弹命中却零伤害。开启后所有碰撞盒（载具本体与部件）都会被补上 ATTACK。
     */
    public static boolean allPartsAttackable = DEFAULT_ALL_PARTS_ATTACKABLE;

    /**
     * 是否修改载具实体的可见范围。
     * <p>
     * MTS 客户端渲染不走距离剔除，实际的可见范围由实体的 clientTrackingRange 决定。
     * 置 {@code false} 时保持 MTS 自己的 32 区块。
     */
    public static boolean modifyRenderDistance = DEFAULT_MODIFY_RENDER_DISTANCE;

    /**
     * 载具实体的可见范围，单位为区块
     */
    public static int renderDistance = DEFAULT_RENDER_DISTANCE;

    /**
     * 是否修改引擎音效的传播距离。
     * <p>
     * MTS 用自己的声音系统播放音效，音量按距离衰减，可听上限取音效自己声明的 maxDistance
     * （未声明时为 32 格）。置 {@code true} 时把引擎部件音效的可听上限统一改为 {@link #vehicleSoundDistance}，
     * 衰减曲线的形状按比例保留。只作用于引擎部件，其他部件与车体自身的音效不受影响。
     */
    public static boolean modifyVehicleSoundDistance = DEFAULT_MODIFY_VEHICLE_SOUND_DISTANCE;

    /**
     * 引擎音效的传播距离，单位为区块
     */
    public static int vehicleSoundDistance = DEFAULT_VEHICLE_SOUND_DISTANCE;

    /**
     * 允许直接摧毁载具的速度下限，单位为 km/h，0 表示不提高。
     * <p>
     * 车包每辆车已经有一个 {@code crashSpeedDestroyed}，实际行驶速度超过它才会一撞即毁。
     * 本项把该下限抬到"车包值"和"本值"里较高的那个，所以只有提高、不会降低，填 0 等价于关掉。
     * 默认 150 是为了盖过官方车包现役车辆的最高速（scout 约 75km/h、merc230 约 104km/h），
     * 让它们不论怎么撞都不会被"直接摧毁"，只掉血。
     * 只作用于定义了 {@code crashSpeedMax} 的车，走硬度分支的车（如 ft17）不受影响。
     */
    public static double vehicleCrashDestroySpeed = DEFAULT_VEHICLE_CRASH_DESTROY_SPEED;

    /**
     * 碰撞摧毁伤害的系数，决定"以车包原始血量为基准"撞一次扣多少。
     * <p>
     * MTS 算的是 {@code 血量上限 × 速度曲线比例}，而"血量上限"已经被 {@link #vehicleHealthScale} 放大过，
     * 所以这里会把 {@link #vehicleHealthScale} 除掉再乘，让撞击伤害不受血量缩放影响 ——
     * 否则拉高血量上限会让撞击伤害同步变大，血量白加，撞几次照样报废。
     * 结果是：100km/h 撞击扣掉 {@code 该车原始血量 × 1.0} 左右，能扛的撞击次数正好等于 {@link #vehicleHealthScale}。
     * <p>
     * 默认 0.65 是按官方车包的碰撞曲线反推的：车包 {@code crashSpeedMin} 10、{@code crashSpeedMax} 55，
     * 配合默认 {@code carSpeedFactor} 0.35，100km/h 处曲线比例是 1.5414，取倒数即 0.649。
     * 注意它跟着服务端的 {@code carSpeedFactor} 走，改过该值的话对应的速度也会变。
     */
    public static double vehicleDestructionDamageScale = DEFAULT_VEHICLE_DESTRUCTION_DAMAGE_SCALE;

    private CbraMtsConfig() {
    }

    /**
     * 从配置目录加载配置；文件不存在时按默认值创建。
     *
     * @param configFile 配置文件的绝对路径
     */
    public static void load(Path configFile) {
        if (Files.exists(configFile)) {
            try (InputStream inputStream = Files.newInputStream(configFile);
                 InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

                JsonElement element = JsonParser.parseReader(reader);
                if (element.isJsonObject()) {
                    _read(element.getAsJsonObject());
                } else {
                    CbraMts.LOGGER.warn("Skipped non jsonObject config file {}, using defaults", configFile);
                }
            } catch (IOException | RuntimeException exception) {
                CbraMts.LOGGER.error("Failed to read config file {}, using defaults", configFile, exception);
            }
        }

        _write(configFile);
    }

    /**
     * 逐项读取，键名见 {@link CbraMtsConfigTag}。
     */
    private static void _read(JsonObject jsonObject) {
        vehicleHealthScale = Math.max(MIN_VEHICLE_HEALTH_SCALE,
                JsonUtils.getJsonDouble(jsonObject, CbraMtsConfigTag.VEHICLE_HEALTH_SCALE, DEFAULT_VEHICLE_HEALTH_SCALE));
        allPartsAttackable = JsonUtils.getJsonBool(jsonObject, CbraMtsConfigTag.ALL_PARTS_ATTACKABLE, DEFAULT_ALL_PARTS_ATTACKABLE);
        modifyRenderDistance = JsonUtils.getJsonBool(jsonObject, CbraMtsConfigTag.MODIFY_RENDER_DISTANCE, DEFAULT_MODIFY_RENDER_DISTANCE);
        renderDistance = Math.max(MIN_RENDER_DISTANCE,
                JsonUtils.getJsonInt(jsonObject, CbraMtsConfigTag.RENDER_DISTANCE, DEFAULT_RENDER_DISTANCE));
        modifyVehicleSoundDistance = JsonUtils.getJsonBool(jsonObject, CbraMtsConfigTag.MODIFY_VEHICLE_SOUND_DISTANCE, DEFAULT_MODIFY_VEHICLE_SOUND_DISTANCE);
        vehicleSoundDistance = Math.max(MIN_VEHICLE_SOUND_DISTANCE,
                JsonUtils.getJsonInt(jsonObject, CbraMtsConfigTag.VEHICLE_SOUND_DISTANCE, DEFAULT_VEHICLE_SOUND_DISTANCE));
        vehicleCrashDestroySpeed = Math.max(MIN_VEHICLE_CRASH_DESTROY_SPEED,
                JsonUtils.getJsonDouble(jsonObject, CbraMtsConfigTag.VEHICLE_CRASH_DESTROY_SPEED, DEFAULT_VEHICLE_CRASH_DESTROY_SPEED));
        vehicleDestructionDamageScale = Math.max(MIN_VEHICLE_DESTRUCTION_DAMAGE_SCALE,
                JsonUtils.getJsonDouble(jsonObject, CbraMtsConfigTag.VEHICLE_DESTRUCTION_DAMAGE_SCALE, DEFAULT_VEHICLE_DESTRUCTION_DAMAGE_SCALE));
    }

    /**
     * 把配置按固定顺序回写，便于使用者直接编辑。
     */
    private static void _write(Path configFile) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(CbraMtsConfigTag.VEHICLE_HEALTH_SCALE, vehicleHealthScale);
        jsonObject.addProperty(CbraMtsConfigTag.ALL_PARTS_ATTACKABLE, allPartsAttackable);
        jsonObject.addProperty(CbraMtsConfigTag.MODIFY_RENDER_DISTANCE, modifyRenderDistance);
        jsonObject.addProperty(CbraMtsConfigTag.RENDER_DISTANCE, renderDistance);
        jsonObject.addProperty(CbraMtsConfigTag.MODIFY_VEHICLE_SOUND_DISTANCE, modifyVehicleSoundDistance);
        jsonObject.addProperty(CbraMtsConfigTag.VEHICLE_SOUND_DISTANCE, vehicleSoundDistance);
        jsonObject.addProperty(CbraMtsConfigTag.VEHICLE_CRASH_DESTROY_SPEED, vehicleCrashDestroySpeed);
        jsonObject.addProperty(CbraMtsConfigTag.VEHICLE_DESTRUCTION_DAMAGE_SCALE, vehicleDestructionDamageScale);

        try {
            Path parent = configFile.getParent();
            if (parent != null) Files.createDirectories(parent);

            Files.writeString(configFile, JsonUtils.toJsonString(jsonObject), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            CbraMts.LOGGER.error("Failed to write config file {}", configFile, exception);
        }
    }
}
