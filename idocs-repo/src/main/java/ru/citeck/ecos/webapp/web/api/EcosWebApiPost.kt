package ru.citeck.ecos.webapp.web.api

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.extensions.webscripts.*
import ru.citeck.ecos.webapp.lib.web.webapi.content.stream.WebApiStreamUtils
import ru.citeck.ecos.webapp.lib.web.webapi.executor.EcosWebExecutorsService
import ru.citeck.ecos.webapp.lib.web.webapi.executor.WebExecutorsRequest
import java.io.InputStream
import java.io.OutputStream
import javax.annotation.PostConstruct

class EcosWebApiPost : AbstractWebScript() {

    @PostConstruct
    fun init() {
    }

    private lateinit var executorsService: EcosWebExecutorsService

    override fun execute(req: WebScriptRequest, res: WebScriptResponse) {
        executorsService.execute(RequestImpl(req, res))
        res.setStatus(200)
    }

    @Autowired
    fun setExecutorsService(executorsService: EcosWebExecutorsService) {
        this.executorsService = executorsService
    }

    private class RequestImpl(
        private val request: WebScriptRequest,
        private val response: WebScriptResponse
    ) : WebExecutorsRequest {

        override fun getOutputStream(): OutputStream {
            return WebApiStreamUtils.nonCloseableOutputStream(response.outputStream)
        }

        override fun getInputStream(): InputStream {
            return WebApiStreamUtils.nonCloseableInputStream(request.content.inputStream)
        }
    }
}
