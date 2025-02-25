package org.mickey;


import com.fazecast.jSerialComm.SerialPort;

public class SerialPortConfiguration {
    /**
     * 端口名称
     */
    private String portName;
    /**
     * 波特率
     */
    private Integer baudRate = 115200;
    /**
     * 数据位
     */
    private Integer dataBits = 8;
    /**
     * 停止位
     */
    private Integer stopBits = 1;
    /**
     * 校验位
     */
    private Integer parity = 0;

    private Integer slaveId = 1;
    private Integer startIndex = 0;

    public SerialPortConfiguration() {

    }

    public SerialPortConfiguration(String portName) {
        this.portName = portName;
    }

    public SerialPort getSerialPort() {
        return SerialPort.getCommPort(this.portName);
    }

    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    public Integer getBaudRate() {
        return baudRate;
    }

    public void setBaudRate(Integer baudRate) {
        this.baudRate = baudRate;
    }

    public Integer getDataBits() {
        return dataBits;
    }

    public void setDataBits(Integer dataBits) {
        this.dataBits = dataBits;
    }

    public Integer getStopBits() {
        return stopBits;
    }

    public void setStopBits(Integer stopBits) {
        this.stopBits = stopBits;
    }

    public Integer getParity() {
        return parity;
    }

    public void setParity(Integer parity) {
        this.parity = parity;
    }

    public Integer getSlaveId() {
        return slaveId;
    }

    public void setSlaveId(Integer slaveId) {
        this.slaveId = slaveId;
    }

    public Integer getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
    }
}
