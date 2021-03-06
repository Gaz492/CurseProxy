package moe.nikky.curseproxy

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.gson.gson
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import kotlinx.html.*
import moe.nikky.curseproxy.addon.AddonRepo
import moe.nikky.curseproxy.addon.FileRepo
import moe.nikky.curseproxy.addon.IDCache
import moe.nikky.curseproxy.exceptions.*
import moe.nikky.setup
import org.slf4j.Logger
import org.slf4j.LoggerFactory


val LOG: Logger = LoggerFactory.getLogger("curseproxy")

const val REST_ENDPOINT = "/api/addon"

fun Application.main() {

    install(DefaultHeaders)
    install(CallLogging)
    //TODO: enable in production
//    install(HttpsRedirect)
//    install(HSTS)
//    install(CORS) {
//        maxAge = Duration.ofDays(1)
//    }
//    install(Metrics) {
//        val reporter = Slf4jReporter.forRegistry(registry)
//                .outputTo(log)
//                .convertRatesTo(TimeUnit.SECONDS)
//                .convertDurationsTo(TimeUnit.MILLISECONDS)
//                .build()
//        reporter.start(10, TimeUnit.SECONDS)
//    }
    install(ContentNegotiation) {
        gson {
            setup()
            setPrettyPrinting()
        }
    }

    routing {
        get(REST_ENDPOINT) {
            LOG.debug("Get all AddOns")
            val addons = AddonRepo.get()
            LOG.info("addon count: ${addons.count()}")

            call.respond(addons)
        }

        get("$REST_ENDPOINT/{addonID}") {
            val addonID = call.parameters["addonID"]?.toInt()
                    ?: throw MissingParameterException("addonID")
            LOG.debug("Get AddOn with addonID=$addonID")
            with(AddonRepo.get(addonID)) {
                call.respond(this)
            }
        }

        get("$REST_ENDPOINT/{addonID}/description") {
            val addonID = call.parameters["addonID"]?.toInt()
                    ?: throw MissingParameterException("addonID")
            LOG.debug("Get AddOn Description with addonID=$addonID")
            call.respondText(AddonRepo.getDescription(addonID), contentType = ContentType.parse("text/html"))
        }

        get("$REST_ENDPOINT/{addonID}/files") {
            val addonID = call.parameters["addonID"]?.toInt() ?: throw MissingParameterException("addonID")
            val files = FileRepo.get(addonID)
            call.respond(files)
        }

        get("$REST_ENDPOINT/{addonID}/files/{fileID}") {
            val addonID = call.parameters["addonID"]?.toInt()
                    ?: throw MissingParameterException("addonID")
            val fileID = call.parameters["fileID"]?.toInt()
                    ?: throw MissingParameterException("fileID")
            call.respond(FileRepo.get(addonID, fileID))
        }

        get("$REST_ENDPOINT/{addonID}/files/{fileID}/changelog") {
            val addonID = call.parameters["addonID"]?.toInt()
                    ?: throw MissingParameterException("addonID")
            val fileID = call.parameters["fileID"]?.toInt()
                    ?: throw MissingParameterException("fileID")
            with(FileRepo.getChangelog(addonID, fileID)) {
                call.respondText(this, contentType = ContentType.parse("text/html"))
            }
        }

        get("/api/ids") {
            val idMap = IDCache.getIDMap()
            call.respond(idMap)
        }

//        get(REST_ENDPOINT) {
//            LOG.debug("Get all Person entities")
//            call.respond(PersonRepo.getAll())
//        }
//        delete("${REST_ENDPOINT}/{id}") {
//            val id = call.parameters["id"] ?: throw IllegalArgumentException("Parameter id not found")
//            LOG.debug("Delete Person entity with Id=$id")
//            call.respondSuccessJson(PersonRepo.remove(id))
//        }
//        delete(REST_ENDPOINT) {
//            LOG.debug("Delete all Person entities")
//            PersonRepo.clear()
//            call.respondSuccessJson()
//        }
//        post(REST_ENDPOINT) {
//            val receive = call.receive<Person>()
//            println("Received Post Request: $receive")
//            call.respond(PersonRepo.add(receive))
//        }
        get("/") {
            call.respondHtml {
                head {
                    title("CurseProxy API")
                }
                body {
                    h1 { +"CurseProxy API" }
                    p {
                        +"Hello World"
                    }
                    p {
                        +"How are you doing?"
                    }
                    a(href = "/api/addon/") { +"get started here" }
                }
            }
        }
        get("/debug/") {
            val scheme = call.request.header("X-Forwarded-Proto") ?: call.request.local.scheme
            val host = call.request.header("Host") ?: "${call.request.local.host}:${call.request.local.port}"
            call.respondHtml {
                head {
                    title("CurseProxy API")
                }
                body {
                    h1 { +"CurseProxy API debug" }
                    p {
                        +"Hello World"
                    }
                    p {
                        +"scheme = $scheme"
                    }
                    p {
                        +"host = $host"
                    }
                    h2 { +"call.request.local" }
                    listOf(
                            "scheme = ${call.request.local.scheme}",
                            "version = ${call.request.local.version}",
                            "port = ${call.request.local.port}",
                            "host = ${call.request.local.host}",
                            "uri = ${call.request.local.uri}",
                            "method = ${call.request.local.method}"
                    ).forEach { p { +it } }

                    h2 { +"Headers" }
                    call.request.headers.entries().forEach {(key, value) ->
                        p {
                            +"$key = $value"
                        }
                    }
                }
            }
        }
    }
    install(StatusPages) {
        exception<Throwable> { cause ->
            call.respond(
                    HttpStatusCode.InternalServerError,
                    StackTraceMessage(cause)
            )
        }
        exception<AddOnNotFoundException> { cause ->
            call.respond(
                    HttpStatusCode.NotFound,
                    cause
            )
        }
        exception<AddOnFileNotFoundException> { cause ->
            call.respond(
                    HttpStatusCode.NotFound,
                    cause
            )
        }
        exception<MissingParameterException> { cause ->
            call.respond(
                    HttpStatusCode.NotAcceptable,
                    cause
            )
        }
        exception<MessageException> { cause ->
            call.respond(
                    HttpStatusCode.NotAcceptable,
                    cause
            )
        }
        exception<IllegalArgumentException> { cause ->
            call.respond(
                    HttpStatusCode.NotAcceptable,
                    StackTraceMessage(cause)
            )
        }
        exception<NumberFormatException> { cause ->
            call.respond(
                    HttpStatusCode.NotAcceptable,
                    StackTraceMessage(cause)
            )
        }
    }

    AddonRepo.sync()
    LOG.info("loading IDs")
    val idMap = IDCache.getIDMap()
    val idCount = idMap.values.sumBy { it.size }
    LOG.info("loaded $idCount IDs")
    AddonRepo.get(287323)
    LOG.info("loaded addon test complete")
}
