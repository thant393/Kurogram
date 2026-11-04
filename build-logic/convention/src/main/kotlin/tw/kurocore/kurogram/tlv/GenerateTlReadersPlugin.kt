package tw.kurocore.kurogram.tlv

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class GenerateTlReadersPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.create("tlReaders", GenerateTlReadersExtension::class.java)

        val androidComponents =
            project.extensions.findByType(AndroidComponentsExtension::class.java)
                ?: error("Apply com.android.application/library before tw.kurocore.kurogram.tl-readers")
        androidComponents.onVariants { variant ->
            val suffix = variant.name.replaceFirstChar { it.uppercase() }
            val task = project.tasks.register<GenerateTlReadersTask>("generate${suffix}TlReaders") {
                schemaDir.set(ext.schemaDir)
                tlrpcFile.set(ext.tlrpcFile)
                outputFile.set(ext.outputFile.orElse("tw/kurocore/kurogram/tlv/TlReaders.java"))
            }

            variant.sources.java?.addGeneratedSourceDirectory(
                task,
                GenerateTlReadersTask::outputFolder
            )
        }
    }
}