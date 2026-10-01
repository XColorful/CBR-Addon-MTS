package xiao.cbra.mts.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xiao.battleroyale.util.JsonUtils;
import xiao.cbra.mts.CbraMts;

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
     * 逐项读取，键名见 {@link xiao.cbra.mts.api.config.CbraMtsConfigTag}。
     */
    private static void _read(JsonObject jsonObject) {
    }

    /**
     * 把配置按固定顺序回写，便于使用者直接编辑。
     */
    private static void _write(Path configFile) {
        JsonObject jsonObject = new JsonObject();

        try {
            Path parent = configFile.getParent();
            if (parent != null) Files.createDirectories(parent);

            Files.writeString(configFile, JsonUtils.toJsonString(jsonObject), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            CbraMts.LOGGER.error("Failed to write config file {}", configFile, exception);
        }
    }
}
