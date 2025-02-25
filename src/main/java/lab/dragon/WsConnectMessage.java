package lab.dragon;

import com.fasterxml.jackson.annotation.JsonRawValue;

public class WsConnectMessage {

    private final WsConnectMessageEnum type;

    private final Object json;

    private WsConnectMessage(Builder builder) {
        this.type = builder.type;
        this.json = builder.json;
    }

    @Override
    public String toString() {
        return "WsConnectMessage{" +
                "type=" + type +
                ", json='" + json + '\'' +
                '}';
    }

    public WsConnectMessageEnum getType() {
        return type;
    }

    public Object getJson() {
        return json;
    }

    public static class Builder {
        private WsConnectMessageEnum type;

        private Object json;

        public Builder type(WsConnectMessageEnum type) {
            this.type = type;
            return this;
        }

        public Builder json(Object json) {
            this.json = json;
            return this;
        }

        public WsConnectMessage build() {
            return new WsConnectMessage(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

}
