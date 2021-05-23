package com.polozov.netty;

import com.polozov.netty.handlers.ByteBufInputHandler;
import com.polozov.netty.handlers.ChatMessageHandler;
import com.polozov.netty.handlers.OutputHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class NettyBaseServer {
	public NettyBaseServer() {
		EventLoopGroup auth = new NioEventLoopGroup(1);
		EventLoopGroup worker = new NioEventLoopGroup();

		try {
			ServerBootstrap bootstrap = new ServerBootstrap();
			bootstrap.group(auth, worker)
					.channel(NioServerSocketChannel.class)
					.childHandler(new ChannelInitializer<>() {
						@Override
						protected void initChannel(Channel ch) throws Exception {
							ch.pipeline().addLast(
									new StringEncoder(),
									new StringDecoder(),
//									new ByteBufInputHandler(), // in-1
//									new OutputHandler(), // out-2
									new ChatMessageHandler()
							);
						}
					});
			ChannelFuture future = bootstrap.bind(4000).sync();
			System.out.println("Server started");
			future.channel().closeFuture().sync();
			System.out.println("Server closed");
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			auth.shutdownGracefully();
			worker.shutdownGracefully();
		}
	}

	public static void main(String[] args) {
		new NettyBaseServer();
	}
}
