package com.puber.utils

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class GitHookInit : DefaultTask() {
    companion object {
        const val TaskName = "gitHookInit"
    }

    @get:InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val hooksDir: File = File(project.rootProject.rootDir, "git_hooks")

    @get:OutputDirectory
    val desinationDir: File = File(project.rootProject.rootDir, ".git${File.separator}hooks")

    @get:Inject
    abstract val fs: FileSystemOperations

    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun copyHooks() {
        hooksDir.listFiles()?.forEach { hook ->
            fs.copy {
                from(hook)
                into(desinationDir)
            }

            if (Os.isFamily(Os.FAMILY_WINDOWS).not()) {
                execOps.exec {
                    commandLine("chmod +x ${File(desinationDir, hook.name)}".split(" "))
                }
            }
        }
    }
}