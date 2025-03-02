document.addEventListener('DOMContentLoaded', function () {
    let ws;
    let wsUrl = "ws://127.0.0.1:8080/ws";
    let wsConnected = false;
    // 示例数据（在实际应用中应从传感器获取实时数据）
    const sensorData = {
        temperature: 25,
        humidity: 60,
        ph: 6.5,
        nitrogen: 50,
        phosphorus: 30,
        potassium: 40
    };

    const elementTemperature = document.getElementById("value-for-temperature");
    const elementHumidity = document.getElementById("value-for-humidity");
    const elementPhValue = document.getElementById("value-for-PH-value");
    const elementNitrogen = document.getElementById("value-for-nitrogen");
    const elementPhosphorus = document.getElementById("value-for-phosphorus");
    const elementPotassium = document.getElementById("value-for-potassium");

    async function createWebSocketAsync() {
        return new Promise((resolve, reject) => {

            ws = new WebSocket(wsUrl);

            ws.onopen = function () {
                wsConnected = true;
                resolve(wsConnected);
            };

            ws.onclose = function () {
                wsConnected = false;

                elementTemperature.innerText = "";
                elementHumidity.innerText = "";
                elementPhValue.innerText = "";
                elementNitrogen.innerText = "";
                elementPhosphorus.innerText = "";
                elementPotassium.innerText = "";
            };

            ws.onerror = function (error) {
                logMessage("发生错误: " + error);
            };

            ws.onmessage = function (event) {
                logMessage(event.data);
                const jsonObject = JSON.parse(event.data);
                if (jsonObject["type"] === "data") {
                    sensorData.temperature = jsonObject["json"]["temperature"];
                    sensorData.humidity = jsonObject["json"]["humidity"];
                    sensorData.ph = jsonObject["json"]["PH-value"];
                    sensorData.nitrogen = jsonObject["json"]["nitrogen"];
                    sensorData.phosphorus = jsonObject["json"]["phosphorus"];
                    sensorData.potassium = jsonObject["json"]["potassium"];


                    elementTemperature.innerText = jsonObject["json"]["temperature"];
                    elementHumidity.innerText = jsonObject["json"]["humidity"];
                    elementPhValue.innerText = jsonObject["json"]["PH-value"];
                    elementNitrogen.innerText = jsonObject["json"]["nitrogen"];
                    elementPhosphorus.innerText = jsonObject["json"]["phosphorus"];
                    elementPotassium.innerText = jsonObject["json"]["potassium"];

                    updateStatus();
                } else if (jsonObject["type"] === "log") {
                    logMessage(jsonObject["json"]);
                }
            };
        });
    }

    createWebSocketAsync();

    // 更新状态显示
    function updateStatus() {
        // 温度检查
        checkSensorRange("temperature", sensorData.temperature,
            [{min:15, max:30}], "温度");

        // 湿度检查
        checkSensorRange("humidity", sensorData.humidity,
            [{min:50, max:80}], "湿度");

        // PH值检查
        checkSensorRange("ph", sensorData.ph,
            [{min:5.5, max:7.5}], "PH值");

        // 氮含量检查
        checkSensorRange("nitrogen", sensorData.nitrogen,
            [{min:40, max:60}], "氮含量");

        // 磷含量检查
        checkSensorRange("phosphorus", sensorData.phosphorus,
            [{min:20, max:40}], "磷含量");

        // 钾含量检查
        checkSensorRange("potassium", sensorData.potassium,
            [{min:30, max:50}], "钾含量");

        updatePlantGuide();
    }

    function checkSensorRange(sensorId, value, validRanges, label) {
        const statusElement = document.getElementById(`${sensorId}-status`);
        let isGood = false;

        for (const range of validRanges) {
            if (value >= range.min && value <= range.max) {
                isGood = true;
                break;
            }
        }

        statusElement.className = `status-indicator ${isGood ? 'good' : 'bad'}`;
    }

    function updatePlantGuide() {
        const guideElement = document.getElementById('plant-guide');
        let html = '<h3>种植建议：</h3>';

        // 根据不同植物的适宜条件判断
        if (sensorData.temperature >= 15 && sensorData.temperature <= 30 &&
            sensorData.humidity >= 50 && sensorData.humidity <= 70) {
            html += '<p>适合种植蔬菜类植物（如西红柿、黄瓜等）</p>';
        }

        if (sensorData.temperature >= 20 && sensorData.temperature <= 25 &&
            sensorData.ph >= 5.5 && sensorData.ph <= 6.5) {
            html += '<p>适合种植水果类植物（如草莓、蓝莓等）</p>';
        }

        if (sensorData.nitrogen >= 40 && sensorData.nitrogen <= 60 &&
            sensorData.phosphorus >= 20 && sensorData.phosphorus <= 40) {
            html += '<p>适合种植花卉类植物（如玫瑰、菊花等）</p>';
        }

        guideElement.innerHTML = html;
    }

    // 显示日志信息
    function logMessage(message) {
        const log = document.getElementById('log');
        log.innerHTML += message + '<br>';
        log.scrollTop = log.scrollHeight; // 滚动到最新消息
    }
});
