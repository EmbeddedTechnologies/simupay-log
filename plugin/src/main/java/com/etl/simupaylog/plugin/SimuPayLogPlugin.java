package com.etl.simupaylog.plugin;

import com.android.build.api.instrumentation.InstrumentationScope;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.Variant;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import kotlin.Unit;

/**
 * Gradle plugin that transparently redirects all {@code android.util.Log} calls
 * to {@code com.etl.simupaylog.Log} at bytecode level during the build.
 *
 * <p>Apply this plugin alongside the SimuPay Log library dependency:
 * <pre>
 * plugins {
 *     id 'com.etl.simupay-log'
 * }
 * dependencies {
 *     implementation 'com.github.EmbeddedTechnologies.simupay-log:lib:VERSION'
 * }
 * </pre>
 *
 * <p>No source changes are required — all existing {@code Log.d()}, {@code Log.i()},
 * etc. calls are automatically redirected at compile time.
 */
public class SimuPayLogPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Register once the Android plugin is applied (works with both app and library modules)
        project.getPlugins().withId("com.android.application", p -> register(project));
        project.getPlugins().withId("com.android.library", p -> register(project));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void register(Project project) {
        AndroidComponentsExtension components =
                project.getExtensions().getByType(AndroidComponentsExtension.class);

        components.onVariants(components.selector().all(), variant -> {
                ((Variant) variant).getInstrumentation().transformClassesWith(
                        LogRedirectClassVisitorFactory.class,
                        InstrumentationScope.PROJECT,
                        params -> Unit.INSTANCE
                );
                return Unit.INSTANCE;
        });
    }
}
