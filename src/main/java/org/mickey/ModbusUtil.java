package org.mickey;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ModbusUtil implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ModbusUtil.class);
    // 常量定义
    private static final int FRAME_LENGTH = 17;
    private final SerialPort serialPort;
    private static final int BUFFER_SIZE = 1024;
    private static final BlockingQueue<ByteBuffer> dataQueue = new ArrayBlockingQueue<>(10);
    private static volatile ModbusUtil instance;
    private final Thread readThread;

    private ModbusUtil(SerialPortConfiguration config) throws IOException {

        this.serialPort = config.getSerialPort();
        if (!serialPort.openPort()) {
            throw new IOException("Failed to open serial port: " + config.getPortName());
        }
        log.debug("打开 {} 端口成功", config.getPortName());
        // 配置串口参数
        this.serialPort.setBaudRate(config.getBaudRate());
        this.serialPort.setNumDataBits(config.getDataBits());
        this.serialPort.setNumStopBits(config.getStopBits());
        this.serialPort.setParity(config.getParity());

        serialPort.addDataListener(new DataListener());
        readThread = new Thread(new ByteBufferProcessor());
        readThread.start();
    }

    public static ModbusUtil getInstance(SerialPortConfiguration config) throws IOException {
        if (instance == null) {
            synchronized (ModbusUtil.class) {
                if (instance == null) {
                    instance = new ModbusUtil(config);
                }
            }
        }
        return instance;
    }

    @Override
    public void close() {
        serialPort.removeDataListener();
        if (serialPort.isOpen()) {
            serialPort.closePort();
        }
        readThread.interrupt();
        log.error("关闭串口");
    }

    public void writeData(byte[] data) throws IOException {
        if (!serialPort.isOpen()) {
            throw new IOException("Serial port is not open");
        }
        this.serialPort.writeBytes(data, data.length);
        log.debug("send command: {}", ByteBufferProcessor.bytesToHex(data));
    }

    private static class ByteBufferProcessor implements Runnable {
        private static final Queue<byte[]> receivedDataQueue = new LinkedList<>();
        private static final byte[] frameTmp = new byte[FRAME_LENGTH];
        private static int index = 0;
        private static void processReceivedData() {

            while (!receivedDataQueue.isEmpty()) {
                byte[] data = receivedDataQueue.poll();
                log.debug("poll data: {}", bytesToHex(data));
                System.arraycopy(data, 0, frameTmp, index, data.length);
                log.debug("frame: {}", frameTmp);
                index+= data.length;
                if (index >= 17) {
                    index = 0;
                    handleFrame();
                }
            }
        }

        private static void handleFrame() {
            System.out.println("接收到一帧数据：" + bytesToHex(ByteBufferProcessor.frameTmp));

            Map<String, Object> result = new HashMap<>(6);
            result.put("temperature", readShort(ByteBufferProcessor.frameTmp, 3) / 10.0);
            result.put("humidity", readShort(ByteBufferProcessor.frameTmp, 5) / 10.0);
            result.put("PH-value", readShort(ByteBufferProcessor.frameTmp, 7) / 100.0);
            result.put("nitrogen", readShort(ByteBufferProcessor.frameTmp, 9));
            result.put("phosphorus", readShort(ByteBufferProcessor.frameTmp, 11));
            result.put("potassium", readShort(ByteBufferProcessor.frameTmp, 13));

            ConnectionWebSocket.SEND_MESSAGE_QUEUE.add(WsConnectMessage.builder().type(WsConnectMessageEnum.data).json(result).build());
        }

        private static String bytesToHex(byte[] bytes) {
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        }

        private static short readShort(byte[] buffer, int index) {
            // 确保有足够的字节可读
            if (buffer.length - index < 2) {
                throw new IllegalArgumentException("Not enough bytes to read a short.");
            }

            // 大端序：高位在前，低位在后
            return (short) (((buffer[index] & 0xFF) << 8) | (buffer[index + 1] & 0xFF));
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                processReceivedData();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private class DataListener implements SerialPortDataListener {
        @Override
        public int getListeningEvents() {
            return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
        }

        @Override
        public void serialEvent(SerialPortEvent event) {
            // 仅当接收到数据时处理

            if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                int available = serialPort.bytesAvailable();

                // 如果读取到数据
                byte[] bytes = new byte[available];
                int bytesRead = serialPort.readBytes(bytes, available);

                if (bytesRead > 0) {
                    // 将接收到的数据添加到队列中
                    ByteBufferProcessor.receivedDataQueue.add(bytes);
                }
            }
        }
    }
}
