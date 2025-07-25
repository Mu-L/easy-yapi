package com.itangcent.idea.psi

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.itangcent.common.constant.Attrs
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.asBool
import com.itangcent.common.utils.sub
import com.itangcent.idea.plugin.api.export.AdditionalField
import com.itangcent.idea.plugin.api.export.core.AdditionalParseHelper
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.yapi.YapiClassExportRuleKeys
import com.itangcent.idea.plugin.rule.suvRuleContext
import com.itangcent.idea.utils.RuleComputeListenerRegistry
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.config.rule.RuleContext
import com.itangcent.intellij.config.rule.RuleKey
import com.itangcent.intellij.config.rule.lookUp
import com.itangcent.intellij.jvm.AccessibleField
import com.itangcent.intellij.jvm.JsonOption
import com.itangcent.intellij.jvm.JsonOption.has
import com.itangcent.intellij.jvm.duck.SingleDuckType
import com.itangcent.intellij.jvm.element.ExplicitClass
import com.itangcent.intellij.psi.*
import com.itangcent.order.Order
import java.util.*
import javax.annotation.PostConstruct

/**
 * support config:
 * 1. json.cache.disable
 * support rules:
 * 1. field.parse.before
 * 2. field.parse.after
 * 3. field.order.with
 * 4. json.additional.field
 */
@Singleton
@Order(-10)
open class ContextualPsiClassHelper : DefaultPsiClassHelper() {

    @Inject
    protected lateinit var configReader: ConfigReader

    @Inject(optional = true)
    private val ruleComputeListener: RuleComputeListener? = null

    @Inject
    private lateinit var additionalParseHelper: AdditionalParseHelper

    private val parseContext: ThreadLocal<Deque<String>> = ThreadLocal()
    private val parseScriptContext = ParseScriptContextImpl()

    @PostConstruct
    fun initRuleComputeListener() {
        (ruleComputeListener as? RuleComputeListenerRegistry)?.register(InnerComputeListener())
    }

    override fun beforeParseClass(
        psiClass: PsiClass,
        resolveContext: ResolveContext,
        fields: MutableMap<String, Any?>
    ) {
        tryInitParseContext()
        ruleComputer.computer(ClassExportRuleKeys.JSON_CLASS_PARSE_BEFORE, psiClass)
        super.beforeParseClass(psiClass, resolveContext, fields)
    }

    override fun beforeParseType(
        psiClass: PsiClass,
        duckType: SingleDuckType,
        resolveContext: ResolveContext,
        fields: MutableMap<String, Any?>,
    ) {
        tryInitParseContext()
        ruleComputer.computer(ClassExportRuleKeys.JSON_CLASS_PARSE_BEFORE, duckType, psiClass)
        super.beforeParseType(psiClass, duckType, resolveContext, fields)
    }

    override fun afterParseClass(psiClass: PsiClass, resolveContext: ResolveContext, fields: MutableMap<String, Any?>) {
        try {
            super.afterParseClass(psiClass, resolveContext, fields)
            computeAdditionalField(psiClass, resolveContext, fields)
            ruleComputer.computer(ClassExportRuleKeys.JSON_CLASS_PARSE_AFTER, psiClass)
        } finally {
            tryCleanParseContext()
        }
    }

    override fun afterParseType(
        psiClass: PsiClass,
        duckType: SingleDuckType,
        resolveContext: ResolveContext,
        fields: MutableMap<String, Any?>,
    ) {
        try {
            super.afterParseType(psiClass, duckType, resolveContext, fields)
            computeAdditionalField(psiClass, resolveContext, fields)
            ruleComputer.computer(ClassExportRuleKeys.JSON_CLASS_PARSE_AFTER, duckType, psiClass)
        } finally {
            tryCleanParseContext()
        }
    }

    protected open fun tryInitParseContext() {
        if (parseContext.get() == null) {
            parseContext.set(LinkedList())
            clearCachePotentially()
        }
    }

    protected open fun tryCleanParseContext() {
        val context = parseContext.get()
        if (context.isNullOrEmpty()) {
            parseContext.remove()
            clearCachePotentially()
        }
    }

    private fun clearCachePotentially() {
        if (configReader.first("json.cache.disable").asBool() == true) {
            devEnv?.dev {
                logger.info("Clearing JSON cache due to json.cache.disable=true")
            }
            resolvedInfo.clear()
            LOG.debug("JSON cache cleared successfully")
        } else {
            LOG.trace("JSON cache remains enabled")
        }
    }

    override fun beforeParseField(
        accessibleField: AccessibleField,
        resourcePsiClass: ExplicitClass,
        resolveContext: ResolveContext,
        fields: MutableMap<String, Any?>,
    ): Boolean {
        pushField(accessibleField.jsonFieldName())
        ruleComputer.computer(ClassExportRuleKeys.JSON_FIELD_PARSE_BEFORE, accessibleField)

        return super.beforeParseField(
            accessibleField,
            resourcePsiClass,
            resolveContext,
            fields
        )
    }

    private fun pushField(fieldName: String) {
        parseContext.get()?.add(fieldName)
        devEnv?.dev {
            logger.info("path -> ${parseScriptContext.path()}")
        }
    }

    override fun onIgnoredParseField(
        accessibleField: AccessibleField,
        resourcePsiClass: ExplicitClass,
        resolveContext: ResolveContext,
        fields: MutableMap<String, Any?>
    ) {
        super.onIgnoredParseField(
            accessibleField,
            resourcePsiClass,
            resolveContext,
            fields
        )
        popField()
    }

    override fun collectFields(explicitClass: ExplicitClass, option: Int): Collection<DefaultAccessibleField> {
        LOG.debug("Collecting fields for class: ${explicitClass.name()}")
        var fields = super.collectFields(explicitClass, option)

        if (ruleLookUp.lookUp(ClassExportRuleKeys.FIELD_ORDER_WITH).isNotEmpty()) {
            LOG.debug("Applying field ordering rules")
            try {
                fields = fields.asSequence().withIndex().sortedWith { (index1, field1), (index2, field2) ->
                    ruleComputer.computer(
                        ruleKey = ClassExportRuleKeys.FIELD_ORDER_WITH,
                        target = suvRuleContext {
                            setExt("a", field1.explicitElement)
                            setExt("b", field2.explicitElement)
                        },
                        context = null
                    ) ?: (index1.compareTo(index2))
                }.map { it.value }.toList()
                LOG.debug("Successfully sorted ${fields.size} fields")
            } catch (e: Exception) {
                logger.traceError("Failed to sort fields. Please check the rule of ${ClassRuleKeys.FIELD_ORDER}", e)
            }
        }
        LOG.trace("Returning ${fields.size} collected fields")
        return fields
    }

    override fun afterParseField(
        accessibleField: AccessibleField,
        resourcePsiClass: ExplicitClass,
        resolveContext: ResolveContext,
        fields: MutableMap<String, Any?>,
    ) {
        super.afterParseField(
            accessibleField,
            resourcePsiClass,
            resolveContext,
            fields
        )

        ruleComputer.computer(ClassExportRuleKeys.JSON_FIELD_PARSE_AFTER, accessibleField)
        computeAdditionalField(accessibleField.psi, resolveContext, fields)
        popField()
    }

    /**
     * Processes additional fields configured via JSON rules.
     *
     * Expected field format (JSON string):
     * {
     *   "name": "fieldName",
     *   "type": "fieldType",
     *   "desc": "fieldDescription" (optional)
     * }
     *
     * Multiple fields can be specified as newline-separated JSON strings.
     *
     * @param context The PsiElement context for rule computation
     * @param resolveContext Current resolution context
     * @param fields Map to store parsed field values
     */
    protected open fun computeAdditionalField(
        context: PsiElement,
        resolveContext: ResolveContext,
        fields: MutableMap<String, Any?>,
    ) {
        LOG.debug("Checking for additional fields in context: ${context.text}")
        val additionalFields = ruleComputer.computer(ClassExportRuleKeys.JSON_ADDITIONAL_FIELD, context)
        if (!additionalFields.isNullOrBlank()) {
            LOG.info("Found ${additionalFields.lines().size} additional field(s) to process")
            for (additionalField in additionalFields.lines()) {
                try {
                    val field = additionalParseHelper.parseFieldFromJson(additionalField)
                    if (field.name.isNullOrBlank() || field.type.isNullOrBlank()) {
                        logger.warn("Skipping invalid additional field - missing name/type: $additionalField")
                        continue
                    }
                    val fieldName = field.name!!
                    if (fields.containsKey(fieldName)) {
                        logger.warn("Skipping duplicate additional field [$fieldName]")
                        continue
                    }
                    LOG.debug("Processing additional field: $fieldName (${field.type})")
                    resolveAdditionalField(field, context, resolveContext, fields)
                } catch (e: Exception) {
                    logger.traceError("Failed to process additional field: $additionalField", e)
                }
            }
        }
    }

    protected open fun resolveAdditionalField(
        additionalField: AdditionalField,
        context: PsiElement,
        resolveContext: ResolveContext,
        fields: MutableMap<String, Any?>,
    ) {
        val additionalFieldType = duckTypeHelper.resolve(additionalField.type!!, context)
        val fieldName = additionalField.name!!
        if (additionalFieldType == null) {
            fields[fieldName] = null
        } else {
            fields[fieldName] = doGetTypeObject(additionalFieldType, context, resolveContext.next())
        }
        if (resolveContext.option.has(JsonOption.READ_COMMENT)) {
            fields.sub(Attrs.COMMENT_ATTR)[fieldName] = additionalField.desc
        }
    }

    private fun popField() {
        parseContext.get()?.removeLast()
        devEnv?.dev {
            logger.info("path -> ${parseScriptContext.path()}")
        }
    }

    inner class ParseScriptContextImpl : ParseScriptContext {
        override fun path(): String {
            return parseContext.get()?.joinToString(".") ?: ""
        }

        override fun property(property: String): String {
            val context = parseContext.get()
            return if (context.isNullOrEmpty()) {
                property
            } else {
                "${context.joinToString(".")}.$property"
            }
        }
    }

    inner class InnerComputeListener : RuleComputeListener {

        override fun computer(
            ruleKey: RuleKey<*>,
            target: Any,
            context: PsiElement?,
            contextHandle: (RuleContext) -> Unit,
            methodHandle: (RuleKey<*>, Any, PsiElement?, (RuleContext) -> Unit) -> Any?,
        ): Any? {
            return if (JSON_RULE_KEYS.contains(ruleKey)) {
                methodHandle(ruleKey, target, context) {
                    contextHandle(it)
                    it.setExt("fieldContext", parseScriptContext)
                }
            } else {
                methodHandle(ruleKey, target, context, contextHandle)
            }
        }
    }

    companion object : Log() {
        val JSON_RULE_KEYS = arrayOf(
            ClassRuleKeys.FIELD_IGNORE,
            ClassRuleKeys.FIELD_DOC,
            ClassRuleKeys.FIELD_NAME,
            ClassRuleKeys.FIELD_NAME_PREFIX,
            ClassRuleKeys.FIELD_NAME_SUFFIX,
            ClassRuleKeys.JSON_UNWRAPPED,
            ClassRuleKeys.FIELD_ORDER,
            YapiClassExportRuleKeys.FIELD_MOCK,
            YapiClassExportRuleKeys.FIELD_ADVANCED,
            ClassExportRuleKeys.FIELD_DEMO,
            ClassExportRuleKeys.FIELD_DEFAULT_VALUE,
            ClassExportRuleKeys.JSON_FIELD_PARSE_BEFORE,
            ClassExportRuleKeys.JSON_FIELD_PARSE_AFTER,
            ClassExportRuleKeys.FIELD_REQUIRED,
            ClassExportRuleKeys.FIELD_ORDER_WITH
        )
    }
}

interface ParseScriptContext {
    fun path(): String
    fun property(property: String): String
}
