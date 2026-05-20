package clipboard

import com.github.spaceenthusiast.clipboard.CopyFilesRequest
import com.github.spaceenthusiast.clipboard.FileMetadata
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.runBlocking

class ClipboardServiceValidationTest : FunSpec({

    val one = FileMetadata("a.txt", 1, "text/plain")

    test("empty file list is rejected") {
        val fx = Fixtures()
        shouldThrow<IllegalArgumentException> {
            runBlocking { fx.service.copyFiles(CopyFilesRequest(files = emptyList(), ttl = 60)) }
        }
    }

    test("too many files in bundle is rejected") {
        val fx = Fixtures()
        val files = List(fx.appConfig.maxFilesPerBundle + 1) { i -> FileMetadata("f$i", 1, "text/plain") }
        shouldThrow<IllegalArgumentException> {
            runBlocking { fx.service.copyFiles(CopyFilesRequest(files = files, ttl = 60)) }
        }
    }

    test("filename containing slash is rejected") {
        val fx = Fixtures()
        shouldThrow<IllegalArgumentException> {
            runBlocking {
                fx.service.copyFiles(CopyFilesRequest(listOf(FileMetadata("a/b", 1, "x")), ttl = 60))
            }
        }
    }

    test("filename containing parent traversal is rejected") {
        val fx = Fixtures()
        shouldThrow<IllegalArgumentException> {
            runBlocking {
                fx.service.copyFiles(CopyFilesRequest(listOf(FileMetadata("a..b", 1, "x")), ttl = 60))
            }
        }
    }

    test("filename too long is rejected") {
        val fx = Fixtures()
        val long = "a".repeat(256)
        shouldThrow<IllegalArgumentException> {
            runBlocking {
                fx.service.copyFiles(CopyFilesRequest(listOf(FileMetadata(long, 1, "x")), ttl = 60))
            }
        }
    }

    test("zero-byte file is rejected") {
        val fx = Fixtures()
        shouldThrow<IllegalArgumentException> {
            runBlocking {
                fx.service.copyFiles(CopyFilesRequest(listOf(FileMetadata("a", 0, "x")), ttl = 60))
            }
        }
    }

    test("per-file size cap is enforced") {
        val fx = Fixtures()
        shouldThrow<IllegalArgumentException> {
            runBlocking {
                fx.service.copyFiles(
                    CopyFilesRequest(
                        files = listOf(FileMetadata("a", fx.appConfig.perFileMaxBytes + 1, "x")),
                        ttl = 60,
                    )
                )
            }
        }
    }

    test("bundle total size cap is enforced") {
        val fx = Fixtures()
        val each = fx.appConfig.perFileMaxBytes
        val countNeeded = (fx.appConfig.bundleMaxBytes / each).toInt() + 1
        val files = List(countNeeded) { i -> FileMetadata("f$i", each, "x") }
        shouldThrow<IllegalArgumentException> {
            runBlocking { fx.service.copyFiles(CopyFilesRequest(files = files, ttl = 60)) }
        }
    }

    test("ttl above cap is rejected") {
        val fx = Fixtures()
        shouldThrow<IllegalArgumentException> {
            runBlocking {
                fx.service.copyFiles(
                    CopyFilesRequest(listOf(one), ttl = fx.appConfig.fileTtlMaxSeconds + 1)
                )
            }
        }
    }

    test("ttl zero is rejected") {
        val fx = Fixtures()
        shouldThrow<IllegalArgumentException> {
            runBlocking { fx.service.copyFiles(CopyFilesRequest(listOf(one), ttl = 0)) }
        }
    }

    test("pasteLimit zero is rejected") {
        val fx = Fixtures()
        shouldThrow<IllegalArgumentException> {
            runBlocking {
                fx.service.copyFiles(CopyFilesRequest(listOf(one), ttl = 60, pasteLimit = 0))
            }
        }
    }
})
