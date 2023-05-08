package com.plantstein.server.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantstein.server.AppConfig;
import com.plantstein.server.dto.CheckPlantConditionsDTO;
import com.plantstein.server.dto.RoomConditionDTO;
import com.plantstein.server.model.Moisture;
import com.plantstein.server.model.Plant;
import com.plantstein.server.model.PlantTimeSeries;
import com.plantstein.server.model.Species;
import com.plantstein.server.repository.PlantRepository;
import com.plantstein.server.repository.PlantTimeSeriesRepository;
import com.plantstein.server.repository.RoomRepository;
import com.plantstein.server.rest.RoomRestController;
import com.plantstein.server.util.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CheckPlantConditionSchedule {
    private final MQTTBeans mqtt;
    private final PlantRepository plantRepository;
    private final RoomRepository roomRepository;
    private final RoomRestController roomRestController;
    private final PlantTimeSeriesRepository plantTimeSeriesRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = AppConfig.PLANT_CONDITION_PUBLISH_INTERVAL)
    public void checkPlantConditions() throws JsonProcessingException {
        for (String clientId : roomRepository.getAllClientIds()) {
            List<CheckPlantConditionsDTO> messages = getCheckPlantConditions(clientId);

            if (!messages.isEmpty()) {
                mqtt.mqttOutBound().handleMessage(
                        MessageBuilder
                                .withPayload(objectMapper.writeValueAsString(messages))
                                .setHeader(MqttHeaders.TOPIC, AppConfig.Topic.PLANT_CONDITIONS + "/" + clientId)
                                .build()
                );
//            mqtt.mqttOutBound().handleMessage(
//                    MessageBuilder
//                            .withPayload("hi client " + clientId)
//                            .setHeader(MqttHeaders.TOPIC, "plant-conditions/" + clientId)
//                            .build()
//            );
            }
        }
    }

    public List<CheckPlantConditionsDTO> getCheckPlantConditions(String clientId) {
        List<CheckPlantConditionsDTO> response = new ArrayList<>();
        for (Plant userPlant : plantRepository.findByClientId(clientId)) {
            RoomConditionDTO condition = roomRestController.getCondition(userPlant.getId());
            List<PlantTimeSeries> plantRTSEntries = plantTimeSeriesRepository.findFirst10ByIdOrderByTimestampDesc(userPlant.getId());
            Moisture averageMoisture = Moisture.getAverageMoisture(
                    plantRTSEntries.stream()
                            .map(PlantTimeSeries::getMoisture)
                            .toList()
            );

            Species species = userPlant.getSpecies();

            if (plantRTSEntries.isEmpty())
                return response;

            int brightnessCheck = Utils.checkWithinTargetValue(condition.getBrightness(), species.getPerfectLight(), AppConfig.BRIGHTNESS_SLACK);
            if (brightnessCheck != 0)
                response.add(
                        CheckPlantConditionsDTO.builder()
                                .plantId(userPlant.getId())
                                .plantName(userPlant.getNickname())
                                .message(
                                        brightnessCheck > 0 ?
                                                "It's too bright for " + userPlant.getNickname() + "!"
                                                : "It's not bright enough for " + userPlant.getNickname() + "!"
                                ).build()
                );

            int temperatureCheck = Utils.checkWithinTargetValue(condition.getTemperature(), species.getPerfectTemperature(), AppConfig.TEMPERATURE_SLACK);
            if (temperatureCheck != 0)
                response.add(
                        CheckPlantConditionsDTO.builder()
                                .plantId(userPlant.getId())
                                .plantName(userPlant.getNickname())
                                .message(
                                        temperatureCheck > 0 ?
                                                "It's too hot for " + userPlant.getNickname() + "!"
                                                : "It's too cold enough for " + userPlant.getNickname() + "!").build()
                );

            int humidityCheck = Utils.checkWithinTargetValue(condition.getBrightness(), species.getPerfectLight(), AppConfig.HUMIDITY_SLACK);
            if (humidityCheck != 0)
                response.add(
                        CheckPlantConditionsDTO.builder()
                                .plantId(userPlant.getId())
                                .plantName(userPlant.getNickname())
                                .message(
                                        humidityCheck > 0 ?
                                                "The humidity is too high for " + userPlant.getNickname() + "!"
                                                : "The humidity isn't high enough for " + userPlant.getNickname() + "!"
                                ).build()
                );

            if (averageMoisture != Moisture.OKAY)
                response.add(
                        CheckPlantConditionsDTO.builder()
                                .plantId(userPlant.getId())
                                .plantName(userPlant.getNickname())
                                .message(
                                        Moisture.TOO_DRY.equals(averageMoisture) ?
                                                userPlant.getNickname() + "'s soil is too dry!"
                                                : userPlant.getNickname() + "'s soil is too wet!"
                                ).build()
                );
        }
        return response;
    }
}
