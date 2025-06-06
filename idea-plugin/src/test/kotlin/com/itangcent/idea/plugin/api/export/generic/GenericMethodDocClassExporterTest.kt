package com.itangcent.idea.plugin.api.export.generic

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.methodDocOnly
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.psi.PsiResource
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Test case of [GenericMethodDocClassExporter]
 */
internal class GenericMethodDocClassExporterTest
    : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var classExporter: ClassExporter

    private lateinit var baseControllerPsiClass: PsiClass

    private lateinit var userCtrlPsiClass: PsiClass

    private val settings = Settings()

    override fun beforeBind() {
        super.beforeBind()
        loadSource(Object::class)
        loadSource(java.lang.String::class)
        loadSource(java.lang.Integer::class)
        loadSource(java.lang.Long::class)
        loadSource(Collection::class)
        loadSource(Map::class)
        loadSource(List::class)
        loadSource(LinkedList::class)
        loadSource(LocalDate::class)
        loadSource(LocalDateTime::class)
        loadSource(HashMap::class)
        loadFile("annotation/Public.java")
        loadFile("constant/UserType.java")
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        baseControllerPsiClass = loadClass("api/BaseController.java")!!
        userCtrlPsiClass = loadClass("api/UserCtrl.java")!!

        //clear log
        LoggerCollector.getLog()
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(Logger::class) { it.with(LoggerCollector::class) }

        builder.bind(ClassExporter::class) { it.with(GenericMethodDocClassExporter::class).singleton() }
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
    }

    override fun customConfig(): String {
        return "api.class.parse.before=groovy:logger.info(\"before parse class:\"+it)\n" +
                "api.class.parse.after=groovy:logger.info(\"after parse class:\"+it)\n" +
                "api.method.parse.before=groovy:logger.info(\"before parse method:\"+it)\n" +
                "api.method.parse.after=groovy:logger.info(\"after parse method:\"+it)\n"
    }

    fun testExport() {
        assertFalse(classExporter.support(Request::class))
        assertTrue(classExporter.support(MethodDoc::class))

        val methodDocs = ArrayList<MethodDoc>()
        actionContext.withBoundary {
            classExporter.export(userCtrlPsiClass, methodDocOnly {
                methodDocs.add(it)
            })
        }
        methodDocs[0].let { methodDoc ->
            assertEquals("current ctrl name", methodDoc.name)
            assertEquals("", methodDoc.desc)
            assertEquals(baseControllerPsiClass.methods[0], (methodDoc.resource as PsiResource).resource())
        }
        methodDocs[1].let { methodDoc ->
            assertEquals("say hello", methodDoc.name)
            assertEquals("not update anything", methodDoc.desc)
            assertEquals(userCtrlPsiClass.methods[0], (methodDoc.resource as PsiResource).resource())
        }
        methodDocs[2].let { methodDoc ->
            assertEquals("get user info", methodDoc.name)
            assertTrue(methodDoc.desc.isNullOrEmpty())
            assertEquals(userCtrlPsiClass.methods[1], (methodDoc.resource as PsiResource).resource())
        }

        assertEquals(ResultLoader.load(), LoggerCollector.getLog().toUnixString())
    }
}