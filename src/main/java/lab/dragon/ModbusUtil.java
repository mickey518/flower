package lab.dragon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;

public class ModbusUtil {
    private static ModbusUtil modbusUtil = null;
    private static final Logger log = LoggerFactory.getLogger(ModbusUtil.class);
    private static final BlockingQueue<ByteBuf> byteBufQueue = new ArrayBlockingQueue<>(10);

    private final SerialPort serialPort;
    private ByteBufProcessor byteBufProcessor = null;

    private ModbusUtil(String port) {
        SerialPortConfig serialPortConfig = new SerialPortConfig(port);
        serialPortConfig.setBaudRate(9600);

        this.serialPort = SerialPort.getCommPort(port);
        this.serialPort.setBaudRate(serialPortConfig.getBaudRate());
        this.serialPort.setNumDataBits(serialPortConfig.getDataBits());
        this.serialPort.setNumStopBits(serialPortConfig.getStopBits());
        this.serialPort.setParity(serialPortConfig.getParity());
    }

    public static ModbusUtil getModbusUtil(String port) {
        if (modbusUtil == null) {
            modbusUtil = new ModbusUtil(port);
        }
        modbusUtil.open();
        return modbusUtil;
    }

    public void open() {
        String tmp;
        if (!this.serialPort.openPort()) {
            tmp = String.format("[传感器]%s打开端口失败", this.serialPort.getSystemPortName());
            log.error(tmp);
        } else {
            tmp = String.format("[传感器]%s打开端口成功", this.serialPort.getSystemPortName());
            log.info(tmp);
            log.info("串口缓冲区读取大小：{}", this.serialPort.getDeviceReadBufferSize());

            this.serialPort.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() {
                    return 1;
                }

                @Override
                public void serialEvent(SerialPortEvent event) {
                    // 仅当接收到数据时处理
                    if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                        byte[] buffer = new byte[1024];
                        int bytesRead = serialPort.readBytes(buffer, buffer.length);

                        // 如果读取到数据
                        if (bytesRead > 0) {
                            // 将读取到的数组存放到 buf 中
                            ByteBuf buf = Unpooled.buffer(bytesRead);
                            buf.writeBytes(buffer, 0, bytesRead);

                            // 将 buf 传递给处理线程
                            try {
                                byteBufQueue.put(buf);
                            } catch (InterruptedException e) {
                                log.error(e.getMessage(), e);
                            }
                        }
                    }
                }
            });

            byteBufProcessor = ByteBufProcessor.getByteBufProcessor();
            CompletableFuture.runAsync(byteBufProcessor);
        }
        ConnectionWebSocket.SEND_MESSAGE_QUEUE.add(WsConnectMessage.builder().type(WsConnectMessageEnum.log).json(tmp).build());
    }

    public void close() {
        this.serialPort.flushDataListener();
        this.serialPort.removeDataListener();
        this.serialPort.closePort();
        log.error("关闭串口");
    }

    public void writeQueryCommand() throws IOException {
        OutputStream outputStream = this.serialPort.getOutputStream();
        outputStream.write(DatatypeConverter.parseHexBinary("010300000006C5C8"));
        outputStream.flush();
    }

    private static class ByteBufProcessor implements Runnable {
        private static final ByteBufProcessor byteBufProcessor = new ByteBufProcessor();
        private final ByteBuf heapedBuffer = ByteBufAllocator.DEFAULT.heapBuffer(1024);
        public boolean running = true;
        public static ByteBufProcessor getByteBufProcessor() {
            return byteBufProcessor;
        }

        private ByteBufProcessor() {

        }
        @Override
        public void run() {
            try {
                while (running) {
                    // 从队列中获取 ByteBuf 数据
                    ByteBuf byteBuf = byteBufQueue.take();

                    // 在这里对 ByteBuf 数据处理
                    processByteBuf(byteBuf);
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }

        private void processByteBuf(ByteBuf byteBuf) throws JsonProcessingException {
            heapedBuffer.writeBytes(byteBuf);

            int frameLength = 17;

            // 检查是否有足够数据检测帧尾
            if (heapedBuffer.readableBytes() < frameLength) return;

            ByteBuf completeFrame = heapedBuffer.readBytes(frameLength);

            byte driverId = completeFrame.readByte();
            byte functionCode = completeFrame.readByte();
            byte length = completeFrame.readByte();

            ByteBuf readBytes = completeFrame.readBytes(length);

            Map<String, Object> stringObjectMap = processByteFrame(readBytes);

            ConnectionWebSocket.SEND_MESSAGE_QUEUE.add(WsConnectMessage.builder().type(WsConnectMessageEnum.data).json(stringObjectMap).build());
        }
    }

    public static Map<String, Object> processByteFrame(ByteBuf buffer) {
        Map<String, Object> result = new HashMap<>(6);
        result.put("wendu", buffer.readShort()/10.0);
        result.put("shidu", buffer.readShort()/10.0);
        result.put("ph", buffer.readShort()/100.0);
        result.put("dan", buffer.readShort());
        result.put("lin", buffer.readShort());
        result.put("jia", buffer.readShort());
        return result;
    }
}
