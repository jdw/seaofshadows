package com.github.jdw.seaofshadows.subcommandos.rosetta

import com.github.jdw.seaofshadows.Glob
import com.github.jdw.seaofshadows.core.dom.Canvas
import com.github.jdw.seaofshadows.subcommandos.rosetta.rowhandlers.ClassRowHandler
import com.github.jdw.seaofshadows.subcommandos.rosetta.rowhandlers.InterfaceRowHandler
import com.github.jdw.seaofshadows.importing.types.Type
import com.github.jdw.seaofshadows.importing.types.renderKotlin
import com.github.jdw.seaofshadows.subcommandos.ApiSubcommand
import com.github.jdw.seaofshadows.utils.throws
import com.github.jdw.seaofshadows.webgl.shared.ArrayBuffer
import com.github.jdw.seaofshadows.webgl.shared.ArrayBufferView
import com.github.jdw.seaofshadows.webgl.shared.BufferDataSource
import com.github.jdw.seaofshadows.webgl.shared.DOMString
import com.github.jdw.seaofshadows.webgl.shared.Event
import com.github.jdw.seaofshadows.webgl.shared.Float32Array
import com.github.jdw.seaofshadows.webgl.shared.Int32Array
import com.github.jdw.seaofshadows.webgl.shared.TexImageSource
import java.io.File
import kotlin.reflect.full.createType


class WebGL1: ApiSubcommand(help = "Import WebGL v1 data.", defaultPath = File("webgl1-shared/src/commonMain/kotlin/com/github/jdw/seaofshadows/webgl/shared")) {
    private val irh = InterfaceRowHandler()
    private val crh = ClassRowHandler()

    private val packag3 = "${Glob.GROUP}.webgl.shared"
    private val javascriptTypes = mutableMapOf(
        "boolean" to Boolean::class,
        "object" to Any::class,
        "void" to Nothing::class,
        "any" to Any::class,
        "TexImageSource" to TexImageSource::class)
    private val predefinedSupertypes = mapOf(
        "Event" to Event::class,
        "HTMLCanvasElement" to Canvas::class,
        "DOMString" to DOMString::class, //TODO Both here and in javascriptTypes?
        "BufferDataSource" to BufferDataSource::class)


    init {
        predefinedSupertypes.forEach {
            val name = it.key
            val ktype = it.value.createType()
            Type.NAME_TO_TYPE[name] = Type.ktypeToType(name, ktype)
        }

        javascriptTypes.forEach {
            val name = it.key
            val ktype = it.value.createType()
            Type.NAME_TO_TYPE[name] = Type.ktypeToType(name, ktype)
        }

        Type.NAME_TO_TYPE["ArrayBuffer"] = Type.ktypeToType("ArrayBuffer", ArrayBuffer::class.createType())
        Type.NAME_TO_TYPE["ArrayBufferView"] = Type.ktypeToType("ArrayBufferView", ArrayBufferView::class.createType())
        Type.NAME_TO_TYPE["Int32Array"] = Type.ktypeToType("Int32Array", Int32Array::class.createType())
        Type.NAME_TO_TYPE["Float32Array"] = Type.ktypeToType("Float32Array", Float32Array::class.createType())
    }


    override fun run() {
        handleDeletion()
        var rowCnt = 1
        val rows = Glob.fetchCache(Glob.KHRONOS_WEBGL1_IDL).split("\n")
        for (idx in rows.indices) {
            Glob.debug("${rowCnt++}: ${rows[idx]}")

            val rowTrimmed = rows[idx].trim() //TODO remove everything that is a comment

            if ("" == rowTrimmed) continue

            if (irh.isActive() &&
                crh.isActive()) throw IllegalStateException("Can't build a class and an interface at the same time!")
            val pieces = rowTrimmed.split(" ")
            val firstPiece = pieces.first()

            if ("/*" == firstPiece && pieces.last() == "*/") continue

            if (irh.isActive()) {
                when (firstPiece) {
                    "{", // Only found after interface declaration the row above
                    "//", // Cancels out the whole row, ofc
                    "HTMLImageElement", // Only first at row 647
                    "HTMLCanvasElement", // Only first at row 648
                    "HTMLVideoElement)", // Only first at row 649
                    "typedef" -> noop()
                    "readonly" -> irh.handleReadOnly(rowTrimmed, predefinedSupertypes)
                    "};" -> handleEndOfInterface()
                    "const" -> irh.handleConst(rowTrimmed)
                    "GLenum", "GLsizei", "ArrayBufferView", "GLint", "WebGLBuffer?", "WebGLFramebuffer?",
                    "WebGLProgram?", "WebGLRenderbuffer?", "WebGLTexture?", "WebGLActiveInfo?", "sequence<WebGLShader>?",
                    "sequence<DOMString>?", "[WebGLHandlesContextLoss]", "any", "DOMString?", "WebGLUniformLocation?",
                    "void", "object?", "WebGLShader?", "WebGLShaderPrecisionFormat?", "Float32Array", "sequence<GLfloat>",
                    "GLboolean" -> {
                        if (irh.isHandlingMethod()) irh.handleOngoingMethod(rowTrimmed) // Multiline method definition
                        else irh.handleMethod(rowTrimmed) // First piece is a return type of a new method
                    }
                    else -> throw Exception("Unhandled first word '$firstPiece'!")
                }
            }
            else if (crh.isActive()) {
                when (firstPiece) {
                    "//" -> noop()
                    "};" -> handleEndOfClassScope()
                    else -> crh.handleClassProperty(rowTrimmed)
                }
            }
            else {
                when (firstPiece) {
                    "[Constructor(DOMString",
                    "WebGLRenderingContext", // Will be defined in webgl1-canvas
                    "typedef" -> noop()
                    "interface" -> irh.handleInterface(rowTrimmed, packag3, predefinedSupertypes)
                    "dictionary" -> crh.handleDictionary(rowTrimmed, packag3)
                    "//" -> noop()
                    else -> throw Exception("Unhandled first word '$firstPiece'!")
                }
            }
        }
    }


    private fun handleEndOfClassScope() {
        val skips = setOf("WebGLContextAttributes", "WebGLContextEventInit")
        if (!skips.contains(crh.currentClassBuilder!!.simpleName)) {
            val clazz = crh.currentClassBuilder!!.build()
            var filename = ""
            val code = when (language) {
                "kotlin" -> {
                    filename = "${clazz.simpleName}.kt"
                    clazz.renderKotlin()
                }
                else -> throws()
            }

            code.save(File("${path.path}/${filename}"))
        }
        crh.currentClassBuilder = null
    }


    private fun handleEndOfInterface() {
        val skips: Set<String> = setOf("WebGLRenderingContext")
        if (!skips.contains(irh.currentInterfaceBuilder!!.simpleName)) {
            val interfaze = irh.handleEndOfInterface()
            var filename = ""
            val code = when (language) {
                "kotlin" -> {
                    filename = "${interfaze.simpleName}.kt"
                    interfaze.renderKotlin()
                }
                else -> throws()
            }
            val type = interfaze.createType() //TODO Remove
            Type.NAME_TO_TYPE[interfaze.simpleName!!] = type
            code.save(File("${path.path}/${filename}"))
        }
        irh.currentInterfaceBuilder = null
    }

    private fun noop() {}
}