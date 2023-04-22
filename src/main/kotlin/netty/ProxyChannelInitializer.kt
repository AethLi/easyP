package netty

import io.klogging.NoCoLogging
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.handler.timeout.IdleStateHandler
import model.config.ConfigurationHolder
import model.config.Inbound
import netty.inbounds.HttpProxyServerHandler
import netty.inbounds.SocksServerHandler
import netty.inbounds.WebsocketInbound
import java.util.function.Function
import java.util.stream.Collectors

class ProxyChannelInitializer : NoCoLogging, ChannelInitializer<NioSocketChannel>() {


    override fun initChannel(ch: NioSocketChannel) {

        val localAddress = ch.localAddress()

        val configuration = ConfigurationHolder.configuration
        val portInboundMap =
            configuration.inbounds.stream().collect(Collectors.toMap(Inbound::port, Function.identity()))
        val inbound = portInboundMap[localAddress.port]
        //todo refactor to strategy pattern
        if (inbound != null) {
            when (inbound.protocol) {
                "http" -> {
                    initHttpInbound(ch)
                    return
                }

                "socks5", "socks4", "socks4a" -> {
                    initSocksInbound(ch, inbound)
                    return
                }

                "trojan" -> {
                    initTrojanInbound(ch, inbound)
                    return
                }
            }
        } else {
            logger.error("not support inbound")
            ch.close()
        }
    }

    private fun initSocksInbound(ch: NioSocketChannel, inbound: Inbound) {
        ch.pipeline().addLast(SocksPortUnificationServerHandler())
        ch.pipeline().addLast(SocksServerHandler(inbound))
    }

    private fun initHttpInbound(ch: NioSocketChannel) {
        // http proxy send a http response to client
        ch.pipeline().addLast(
            HttpResponseEncoder()
        )
        // http proxy send a http request to server
        ch.pipeline().addLast(
            HttpRequestDecoder()
        )
        ch.pipeline().addLast(
            HttpProxyServerHandler()
        )
        ch.pipeline().addLast(ChunkedWriteHandler())
        ch.pipeline().addLast("aggregator", HttpObjectAggregator(10 * 1024 * 1024))
        ch.pipeline().addLast("compressor", HttpContentCompressor())
        ch.pipeline().addLast(HttpServerCodec())
    }

    private fun initTrojanInbound(ch: NioSocketChannel, inbound: Inbound) {
        ch.pipeline().addLast(HttpServerCodec())
        ch.pipeline().addLast(ChunkedWriteHandler())
        ch.pipeline().addLast(HttpObjectAggregator(65536))
        ch.pipeline().addLast(IdleStateHandler(60, 60, 60))
        ch.pipeline().addLast(WebsocketInbound())

    }
}
