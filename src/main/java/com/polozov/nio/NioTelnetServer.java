package com.polozov.nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class NioTelnetServer {
	public static final String LS_COMMAND = "\tls    view all files and directories\n";
	public static final String MKDIR_COMMAND = "\tmkdir    create directory\n";
	public static final String CHANGE_NICKNAME = "\tnick    change nickname\n";
	public static final String CAT_COMMAND= "\tcat    read file\n";
	public static final String TOUCH_COMMAND = "\ttouch    create file\n";
	public static final String CD_COMMAND = "\tcd    go to directory\n";
	public static final String RM_COMMAND = "\trm    remove file or directory\n";
	public static final String COPY_COMMAND = "\tcopy    copy file or directory\n";

	private final ByteBuffer buffer = ByteBuffer.allocate(512);
	private HashMap<String, Path> userPaths = new HashMap<>();
	private HashMap<String, String> userNames = new HashMap<>();

	public NioTelnetServer() throws IOException {
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(new InetSocketAddress(5678));
		server.configureBlocking(false);
		// OP_ACCEPT, OP_READ, OP_WRITE
		Selector selector = Selector.open();

		server.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("Server started");

		while (server.isOpen()) {
			selector.select();

			Set<SelectionKey> selectionKeys = selector.selectedKeys();
			Iterator<SelectionKey> iterator = selectionKeys.iterator();

			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				if (key.isAcceptable()) {
					handleAccept(key, selector);
				} else if (key.isReadable()) {
					handleRead(key, selector);
				}
				iterator.remove();
			}
		}
	}

	private void handleRead(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((SocketChannel) key.channel());
		SocketAddress client = channel.getRemoteAddress();
		String address = client.toString();
		int readBytes = channel.read(buffer);
		if (readBytes < 0) {
			channel.close();
			return;
		} else if (readBytes == 0) {
			return;
		}

		buffer.flip();

		StringBuilder sb = new StringBuilder();
		while (buffer.hasRemaining()) {
			sb.append((char) buffer.get());
		}

		buffer.clear();

		// TODO
		// touch [filename] - создание файла
		// mkdir [dirname] - создание директории
		// cd [path] - перемещение по каталогу (.. | ~ )
		// rm [filename | dirname] - удаление файла или папки
		// copy [src] [target] - копирование файла или папки
		// cat [filename] - просмотр содержимого
		// вывод nickname в начале строки

		// NIO
		// NIO telnet server

		if (key.isValid()) {
			String command = sb
					.toString()
					.replace("\n", "")
					.replace("\r", "");
			System.out.println(command);

			if ("--help".equals(command)) {
				sendMessage(LS_COMMAND, selector, client);
				sendMessage(MKDIR_COMMAND, selector, client);
				sendMessage(CHANGE_NICKNAME, selector, client);
				sendMessage(CAT_COMMAND, selector, client);
				sendMessage(TOUCH_COMMAND, selector, client);
				sendMessage(CD_COMMAND, selector, client);
				sendMessage(RM_COMMAND, selector, client);
				sendMessage(COPY_COMMAND, selector, client);
			} else if ("ls".equals(command)) {
				System.out.println(getFileList(client.toString()).concat("\n"));
				sendMessage(getFileList(client.toString()).concat("\n"), selector, client);
			}
			else if (command.startsWith("touch")) {
				String[] args = command.split(" ", 2);
				Path path = Paths.get(userPaths.get(client.toString()).toString(), args[1]);
				if(!Files.exists(path)){
					Files.createFile(path);
				}
			}else if (command.startsWith("mkdir")) {
				String[] args = command.split(" ", 2);
				Path path = Paths.get(userPaths.get(client.toString()).toString(), args[1]);
				if(!Files.exists(path)){
					Files.createDirectory(path);
				}
			}else if (command.startsWith("cd")) {
				String[] args = command.split(" ", 2);
				Path path = Paths.get(userPaths.get(address).toString(), args[1]);
				if(Files.exists(path)){
					userPaths.put(address, path);
				} else{
					sendMessage("Directory do not found", selector, client);
				}
			}else if (command.startsWith("rm")) {
				String[] args = command.split(" ", 2);
				Path path = Paths.get(userPaths.get(address).toString(), args[1]);
				if(Files.exists(path)){
					if(Files.isDirectory(path)){
						Files.walkFileTree(path, new FileVisitor<Path>() {
							@Override
							public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
								return FileVisitResult.CONTINUE;
							}

							@Override
							public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
								Files.delete(file);
								return FileVisitResult.CONTINUE;
							}

							@Override
							public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
								sendMessage("Can not delete file "+ file.toString(), selector, client);
								return FileVisitResult.TERMINATE;
							}

							@Override
							public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
								Files.delete(dir);
								return FileVisitResult.CONTINUE;
							}
						});
					} else{
						Files.delete(path);
					}
				} else{
					sendMessage("Directory or file do not found", selector, client);
				}
			}else if (command.startsWith("copy")) {
				String[] args = command.split(" ", 3);
				Path src = Paths.get(userPaths.get(address).toString(), args[1]);
				Path target = Paths.get(userPaths.get(address).toString(), args[2]);
				if(Files.exists(src)){
					Files.copy(src, target);
				} else{
					sendMessage("Directory or file do not found", selector, client);
				}
			}else if (command.startsWith("cat")) {
				String[] args = command.split(" ", 2);
				Path path = Paths.get(userPaths.get(address).toString(), args[1]);
				if(Files.exists(path) && !Files.isDirectory(path)) {
					List<String> lines = Files.readAllLines(path);
					for(String line: lines){
						sendMessage(line+"\n", selector, client);
					}
				}
			}else if (command.startsWith("nick")) {
				String[] args = command.split(" ", 2);
				userNames.put(address, args[1]);
			}else if ("exit".equals(command)) {
				System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
				channel.close();
				return;
			}
			if (!"exit".equals(command)) {
				sendMessage("["+userNames.get(address)+"] ", selector, client);
			}
		}
	}

	private String getFileList(String address) throws IOException {
		return String.join(" ", new File(userPaths.get(address).toString()).list());
	}

	private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
		for (SelectionKey key : selector.keys()) {
			if (key.isValid() && key.channel() instanceof SocketChannel) {
				if (((SocketChannel)key.channel()).getRemoteAddress().equals(client)) {
					((SocketChannel)key.channel())
							.write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
				}
			}
		}
	}

	private void handleAccept(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
		String address = channel.getRemoteAddress().toString();
		channel.configureBlocking(false);
		System.out.println("Client accepted. IP: " + channel.getRemoteAddress());

		channel.register(selector, SelectionKey.OP_READ, "some attach");

		userPaths.put(address,Path.of("server"));
		userNames.put(address, "user"+(userNames.size()+1));

		channel.write(ByteBuffer.wrap("Hello user!\n".getBytes(StandardCharsets.UTF_8)));
		channel.write(ByteBuffer.wrap("Enter --help for support info\n".getBytes(StandardCharsets.UTF_8)));
	}

	public static void main(String[] args) throws IOException {
		new NioTelnetServer();
	}
}
