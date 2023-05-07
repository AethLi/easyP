package netty


import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import model.config.ConfigurationSettings.Companion.Configuration
import mu.KotlinLogging
import kotlin.system.exitProcess

/**
 * netty服务端配置
 */
class NettyServer {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val bossGroup: EventLoopGroup = NioEventLoopGroup()
    private val workerGroup: EventLoopGroup = NioEventLoopGroup()
    fun start() {
        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup) // 绑定线程池
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .handler(LoggingHandler(LogLevel.DEBUG))
            .childHandler(ProxyChannelInitializer())
            .option(ChannelOption.SO_BACKLOG, 65536)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
        Configuration.inbounds.stream().forEach {
            bootstrap.bind(it.port).addListener { future ->
                if (future.isSuccess) {
                    logger.info("bind ${it.port} success")
                    Runtime.getRuntime().addShutdownHook(Thread({ close() }, "Server Shutdown Thread"))
                } else {
                    logger.error("bind ${it.port} fail, reason:{}", future.cause().message)
                    exitProcess(1)
                }
            }
        }

    }

    /**
     * close gracefully
     */
    private fun close() {
        if (!(bossGroup.isShutdown || bossGroup.isShuttingDown)) {
            bossGroup.shutdownGracefully()
        }
        if (!(workerGroup.isShutdown || workerGroup.isShuttingDown)) {
            workerGroup.shutdownGracefully()
        }
    }
}
