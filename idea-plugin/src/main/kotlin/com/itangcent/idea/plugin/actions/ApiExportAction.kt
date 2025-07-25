package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.cache.DefaultFileApiCacheRepository
import com.itangcent.idea.plugin.api.cache.FileApiCacheRepository
import com.itangcent.idea.plugin.config.EnhancedConfigReader
import com.itangcent.idea.plugin.rule.SuvRuleParser
import com.itangcent.idea.psi.CustomizedPsiClassHelper
import com.itangcent.idea.utils.RuleComputeListenerRegistry
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.findCurrentMethod
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.PsiClassHelper

abstract class ApiExportAction(text: String) : BasicAnAction(text) {

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(RuleParser::class) { it.with(SuvRuleParser::class).singleton() }
        builder.bind(RuleComputeListener::class) { it.with(RuleComputeListenerRegistry::class).singleton() }

        builder.bind(FileApiCacheRepository::class) { it.with(DefaultFileApiCacheRepository::class).singleton() }

        builder.bind(ConfigReader::class) { it.with(EnhancedConfigReader::class).singleton() }
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)
        actionContext.findCurrentMethod()
    }
}