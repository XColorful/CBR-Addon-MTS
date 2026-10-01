package xiao.cbra.mts.api.config;

/**
 * 配置文件中各项的键名。
 */
public class CbraMtsConfigTag {

    /**
     * 全载具生命上限的缩放比例
     */
    public static final String VEHICLE_HEALTH_SCALE = "vehicleHealthScale";

    /**
     * 是否让全部碰撞盒都带上 ATTACK 类型
     */
    public static final String ALL_PARTS_ATTACKABLE = "allPartsAttackable";

    /**
     * 是否修改载具实体的可见范围
     */
    public static final String MODIFY_RENDER_DISTANCE = "modifyRenderDistance";

    /**
     * 载具实体的可见范围，单位为区块
     */
    public static final String RENDER_DISTANCE = "renderDistance";

    /**
     * 是否修改引擎音效的传播距离
     */
    public static final String MODIFY_VEHICLE_SOUND_DISTANCE = "modifyVehicleSoundDistance";

    /**
     * 引擎音效的传播距离，单位为区块
     */
    public static final String VEHICLE_SOUND_DISTANCE = "vehicleSoundDistance";

    /**
     * 允许直接摧毁载具的速度下限，单位为 km/h
     */
    public static final String VEHICLE_CRASH_DESTROY_SPEED = "vehicleCrashDestroySpeed";

    /**
     * 碰撞摧毁伤害的系数
     */
    public static final String VEHICLE_DESTRUCTION_DAMAGE_SCALE = "vehicleDestructionDamageScale";

    private CbraMtsConfigTag() {}
}
