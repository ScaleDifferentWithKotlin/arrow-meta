package arrow.meta.quotes

import arrow.meta.Meta
import arrow.meta.phases.ExtensionPhase
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.getValueParameters
import org.jetbrains.kotlin.psi.psiUtil.modalityModifierType
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

/**
 * [classOrObject] is a function that intercepts all [KtClass] elements that [match]
 * allowing a [Transform] to change the intercepted AST tree before compilation.
 *
 * The [ClassScope] is projected over the template to allow destructuring of the different parts of the
 * [KtClass]. The scope enables template syntax where the user may add new members or modify the class structure
 * before it's compiled
 *
 * ```kotlin:ank:silent
 * import arrow.meta.Meta
 * import arrow.meta.Plugin
 * import arrow.meta.invoke
 * import arrow.meta.quotes.Transform
 * import arrow.meta.quotes.classOrObject
 *
 * val Meta.example: Plugin
 *   get() =
 *     "Example" {
 *       meta(
 *         /** Intercepts all classes named 'Test' **/
 *         classOrObject({ name == "Test" }) { classOrObject ->
 *           Transform.replace(
 *             replacing = classOrObject,
 *             newDeclaration =
 *               """|$`@annotationEntries` $kind $name $`(typeParameters)` $`(valueParameters)` : $supertypes"} {
 *                  |  $body
 *                  |  fun void test(): Unit =
 *                  |    println("Implemented by ΛRROW Meta!")
 *                  |}
 *                  |""".`class`.synthetic
 *           )
 *         }
 *       )
 *     }
 * ```
 */
fun Meta.classOrObject(
  match: KtClass.() -> Boolean,
  map: ClassScope.(KtClass) -> Transform<KtClass>
): ExtensionPhase =
  quote(match, map) { ClassScope(it) }

class ClassScope(
  override val value: KtClass,
  val `@annotationEntries`: ScopedList<KtAnnotationEntry> = ScopedList(value.annotationEntries),
  val modality: Name? = value.modalityModifierType()?.value?.let(Name::identifier),
  val visibility: Name? = value.visibilityModifierType()?.value?.let(Name::identifier),
  val kind: Name? =
    (when {
      value.isSealed() -> "sealed "
      value.isData() -> "data "
      else -> "/* empty? */"
    } + value.getClassOrInterfaceKeyword()?.text).let(Name::identifier),
  val name: Name? = value.nameAsName,
  val `(typeParameters)`: ScopedList<KtTypeParameter> = ScopedList(prefix = "<", value = value.typeParameters, postfix = ">"),
  val `(valueParameters)`: ScopedList<KtParameter> = ScopedList(prefix = "public constructor (", value = value.getValueParameters(), postfix = ")"),
  val supertypes: ScopedList<KtSuperTypeListEntry> = ScopedList(value.superTypeListEntries),
  val body: ClassBodyScope = ClassBodyScope(value.body)
) : Scope<KtClass>(value)

data class ClassBodyScope(val value: KtClassBody?) {
  override fun toString(): String =
    value?.text?.drop(1)?.dropLast(1) ?: ""
}
