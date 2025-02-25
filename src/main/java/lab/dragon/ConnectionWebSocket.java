package lab.dragon;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;


@Component
@ServerEndpoint("/ws")
public class ConnectionWebSocket {
    /**
     * websocket发送数据队列
     */
    public static final LinkedBlockingQueue<WsConnectMessage> SEND_MESSAGE_QUEUE = new LinkedBlockingQueue<>();
    public static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Logger log = LoggerFactory.getLogger(ConnectionWebSocket.class);

    /**
     * 当前websocket的session标识
     */
    private Session session;
    /**
     * 伺服控制modbus模块
     */
    private ModbusUtil servoModbusUtil;
    /**
     * 读取数据的定时线程
     */
    private ScheduledFuture<?> scheduledReadServoFuture;
    private CompletableFuture<Void> SEND_MESSAGE_FUTURE;

    /**
     * 报警信息代码映射表
     */
    private static Map<String, String> warnMessages;
    private static boolean adjustment = false;

    /**
     * 打开连接
     *
     * @param session websocket连接标识符
     */
    @OnOpen
    public void onOpen(Session session) {
        log.info("[ws]创建一个连接：{}", session.getId());

        this.session = session;
        servoModbusUtil = ModbusUtil.getModbusUtil("COM4");

        // 连上 websocket 时，监听 websocket 发送队列，有数据时发送到 socket 前端，session 关闭时，循环中断。
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
        this.scheduledReadServoFuture = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            // 读取伺服报警记录
            try {
                writeQueryCommand();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }, 0, 1, TimeUnit.SECONDS);

    }

    /**
     * 接收信息
     *
     * @param msg 接收到的消息，json格式，由 type 和 json 字符串两部分组成，例如："{"type": "command", "json": "{}"}"
     */
    @OnMessage
    public void onMessage(String msg, Session session) {
        log.info("[ws recv] session: {}, 收到消息 =》 {}", this.session.getId(), msg);
    }

    private void writeQueryCommand() throws IOException {
        servoModbusUtil.writeQueryCommand();
    }

    /**
     * 接收错误信息
     *
     * @param throwable
     */
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
        if (this.scheduledReadServoFuture != null) {
            this.scheduledReadServoFuture.cancel(false);
        }
        if (this.servoModbusUtil != null) {
            this.servoModbusUtil.close();
        }

        try {
            this.session.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}