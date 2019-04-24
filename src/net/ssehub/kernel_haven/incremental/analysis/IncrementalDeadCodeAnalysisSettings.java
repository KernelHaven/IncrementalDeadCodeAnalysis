package net.ssehub.kernel_haven.incremental.analysis;

import static net.ssehub.kernel_haven.config.Setting.Type.BOOLEAN;
import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.undead_analyzer.ThreadedDeadCodeFinder;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

/**
 * The Class IncrementalDeadCodeAnalysisSettings.
 * 
 * @author Moritz
 */
public class IncrementalDeadCodeAnalysisSettings {

    /** The Constant BUILD_MODEL_OPTIMIZATION. */
    public static final Setting<Boolean> BUILD_MODEL_OPTIMIZATION = new Setting<>(
            "incremental.analysis.build_model.optimization", BOOLEAN, true, "FALSE",
            "This setting determines whether information about the differences in the build model"
                    + " compared with the previous build model should be used to reduce the computational effort.");

    public static final Setting<Boolean> CODE_MODEL_OPTIMIZATION = new Setting<>(
            "incremental.analysis.code_model.optimization", BOOLEAN, true, "FALSE",
            "This setting determines whether information about the differences in the code model"
                    + " compared with the previous build model should be used to reduce the computational effort.");

    /** The Constant NUMBER_OF_THREADS. */
    public static final @NonNull Setting<@NonNull Integer> NUMBER_OF_THREADS =
            new Setting<>("analysis.undead.threads", Setting.Type.INTEGER, true, "2",
                    "Number of threads to use for the " + ThreadedDeadCodeFinder.class.getName() + ". Must be >= 1.");
    /**
     * Holds all declared setting constants.
     */
    private static final Set<Setting<?>> SETTINGS = new HashSet<>();

    static {
        for (Field field : IncrementalDeadCodeAnalysisSettings.class.getFields()) {
            if (Setting.class.isAssignableFrom(field.getType()) && Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) {
                try {
                    SETTINGS.add(notNull((Setting<?>) field.get(null)));
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Registers all settings declared in this class to the given configuration
     * object.
     * 
     * @param config The configuration to register the settings to.
     * 
     * @throws SetUpException If any setting restrictions are violated.
     */
    public static void registerAllSettings(Configuration config) throws SetUpException {
        for (Setting<?> setting : SETTINGS) {
            config.registerSetting(setting);
        }
    }

}
