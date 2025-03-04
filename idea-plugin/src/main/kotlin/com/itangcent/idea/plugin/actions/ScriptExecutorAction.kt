package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.cache.CachedRequestClassExporter
import com.itangcent.idea.plugin.api.debug.ScriptExecutor
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.spring.SpringRequestClassExporter
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository

class ScriptExecutorAction : ApiExportAction("ScriptExecutor") {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }
        builder.bind(ClassExporter::class) { it.with(CachedRequestClassExporter::class).singleton() }
        builder.bindInstance("file.save.default", "script.txt")
        builder.bindInstance("file.save.last.location.key", "com.itangcent.debug.loadOrSave.path")

        builder.bind(ScriptExecutor::class) { it.singleton() }

    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)
        val debugger = actionContext.instance(ScriptExecutor::class)
        debugger.showDebugWindow()
    }
}

