package org.mickey;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HexFormat;
import java.util.concurrent.*;

/**
 * WebSocket连接处理类，用于与客户端建立WebSocket连接并处理土壤传感器数据读取和消息发送。
 */
@Component
@ServerEndpoint("/ws")
public class ConnectionWebSocket {
    /**
     * websocket发送数据队列，用于存储待发送的消息对象，线程安全的阻塞队列。
     */
    public static final LinkedBlockingQueue<WsConnectMessage> SEND_MESSAGE_QUEUE = new LinkedBlockingQueue<>();
    private final Logger log = LoggerFactory.getLogger(ConnectionWebSocket.class);

    /**
     * 当前websocket连接会话标识，用于管理当前连接的状态和通信。
     */
    private Session session;
    /**
     * 土壤传感器Modbus通信工具类实例，用于与土壤传感器进行Modbus协议通信。
     */
    private ModbusUtil sensorModbusUtil;
    /**
     * 定时发送土壤传感器ScheduledFuture对象，用于管理定时任务的生命周期。
     */
    private ScheduledFuture<?> scheduledSendReadCommandFuture;
    /**
     * 异步消息发送CompletableFuture对象，用于管理异步消息发送任务的生命周期。
     */
    private CompletableFuture<Void> SEND_MESSAGE_FUTURE;

    /**
     * WebSocket连接打开时调用的方法，初始化传感器通信工具和定时任务。
     *
     * @param session 当前WebSocket连接会话
     */
    @OnOpen
    public void onOpen(Session session) {
        log.info("[ws]创建一个连接：{}", session.getId());

        this.session = session;

        // 初始化串口配置
        SerialPortConfiguration serialPortConfiguration = new SerialPortConfiguration();
        serialPortConfiguration.setPortName("COM5");
        serialPortConfiguration.setBaudRate(9600);

        try {
            sensorModbusUtil = ModbusUtil.getInstance(serialPortConfiguration);
        } catch (IOException e) {
            log.error("初始化传感器通信工具失败", e);
            return;
        }

        // 启动消息发送异步任务
        SEND_MESSAGE_FUTURE = CompletableFuture.runAsync(() -> {
            while (this.session.isOpen()) {
                try {
                    WsConnectMessage take = SEND_MESSAGE_QUEUE.take();
                    session.getBasicRemote().sendText(new ObjectMapper().writeValueAsString(take));
                } catch (IOException | InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
            }
        });


        // 定时获取伺服控制器报警记录的定时线程
        this.scheduledSendReadCommandFuture = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            // 读取伺服报警记录
            try {
                writeQueryCommand();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }, 0, 1, TimeUnit.SECONDS);

    }

    private void writeQueryCommand() throws IOException {
        HexFormat hexFormat = HexFormat.of();

        byte[] bytes = hexFormat.parseHex("010300000006C5C8");

        sensorModbusUtil.writeData(bytes);
    }

    @OnError
    public void onError(Throwable throwable) throws IOException {
        log.error("websocket捕捉到异常，导致websocket关闭。异常信息：{}", throwable.getMessage(), throwable);
        this.session.close();
    }

    @OnClose
    public void onClosing() {
        log.info("[ws]断开连接：{}", this.session.getId());

        if (this.SEND_MESSAGE_FUTURE != null) {
            this.SEND_MESSAGE_FUTURE.cancel(false);
        }
        // 关闭伺服获取数据参数定时线程
        if (this.scheduledSendReadCommandFuture != null) {
            this.scheduledSendReadCommandFuture.cancel(false);
        }
        if (this.sensorModbusUtil != null) {
            this.sensorModbusUtil.close();
        }

        try {
            this.session.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}