package lab.dragon;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * @author mickey.wang
 */
public enum WsConnectMessageEnum {
    @JsonRawValue
    data,
    @JsonRawValue
    log
}
