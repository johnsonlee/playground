import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile

plugins {
    `java`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

@DisableCachingByDefault
abstract class AarTransform : TransformAction<TransformParameters.None> {

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val aar: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = aar.get().asFile
        val outJar = outputs.file("${input.nameWithoutExtension}.jar")
        if (outJar.exists()) {
            outJar.delete()
        }

        println("Transforming $input => $outJar")

        ZipFile(input).use { aar ->
            val classesJar = aar.getEntry("classes.jar")
            val others = setOf(
                "AndroidManifest.xml",
                "R.txt",
                "public.txt",
            ).mapNotNull(aar::getEntry) + aar.entries().asSequence().filterNot {
                it.isDirectory || !it.name.startsWith("res/")
            }

            outJar.outputStream().use { out ->
                JarOutputStream(out).use { jarOut ->
                    // copy classes.jar
                    JarInputStream(aar.getInputStream(classesJar)).use { jarIn ->
                        var entry = jarIn.nextJarEntry
                        while (null != entry) {
                            jarOut.putNextEntry(entry)
                            jarIn.copyTo(jarOut)
                            entry = jarIn.nextJarEntry
                        }
                    }

                    // copy others
                    others.forEach { entry ->
                        val newEntry = JarEntry("AAR-INF/${entry.name}").apply {
                            compressedSize = entry.compressedSize
                            crc = entry.crc
                            method = entry.method
                            size = entry.size
                            time = entry.time
                        }
                        jarOut.putNextEntry(newEntry)
                        aar.getInputStream(entry).copyTo(jarOut)
                    }
                }
            }
        }
    }

}

dependencies {
    registerTransform(AarTransform::class.java) {
        from.attribute(ARTIFACT_TYPE_ATTRIBUTE, "aar")
        to.attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
    }
}

val use by configurations.creating {
    attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
}

dependencies {
    use(libs.androidx.lifecycle.common.java8)
    use(libs.androidx.appcompat)
    use(libs.androidx.core.ktx)
}

val shadowJar by tasks.getting(ShadowJar::class) {
    archiveBaseName.set("libs")
    archiveClassifier.set("all")
    archiveVersion.set(project.version.toString())

    outputs.upToDateWhen { false }

    configurations = listOf(use)
    dependencies {
        exclude(dependency(KotlinClosure1<ResolvedDependency, Boolean>({
            moduleGroup == "org.jetbrains.kotlin" && moduleName.startsWith("kotlin-stdlib")
        })))
    }
    exclude("android/support/**")
    exclude("META-INF/**/*.kotlin_module")
    exclude("META-INF/**/*.version")
    exclude("META-INF/**/pom.xml")
    exclude("META-INF/**/pom.properties")
    exclude("AAR-INF/**")

    doLast {
        use.files.forEach { artifact ->
            val dir = "${artifact.nameWithoutExtension}.aar"
            val dest = rootProject.layout.buildDirectory.dir("aars").get().dir(dir).asFile

            println("Extracting ${artifact.name} => $dest")

            copy {
                from(zipTree(artifact)) {
                    include("AAR-INF/**")
                    eachFile {
                        relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                    }
                    includeEmptyDirs = false
                }
                into(dest)
            }
        }
    }
}

artifacts {
    archives(shadowJar)
}

tasks.shadowJar.configure {
    mustRunAfter(rootProject.tasks.named("prepareKotlinBuildScriptModel"))
}
