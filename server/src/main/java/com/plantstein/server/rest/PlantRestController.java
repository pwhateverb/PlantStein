package com.plantstein.server.rest;

import com.plantstein.server.dto.CheckPlantConditionsDTO;
import com.plantstein.server.dto.NewPlantDTO;
import com.plantstein.server.dto.PlantConditionDTO;
import com.plantstein.server.exception.NotFoundException;
import com.plantstein.server.model.Plant;
import com.plantstein.server.model.PlantTimeSeries;
import com.plantstein.server.model.Room;
import com.plantstein.server.model.Species;
import com.plantstein.server.repository.PlantRepository;
import com.plantstein.server.repository.RoomRepository;
import com.plantstein.server.repository.SpeciesRepository;
import com.plantstein.server.util.Utils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.NotYetImplementedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * The rest controller for all plant related requests.
 */
@RestController
@RequestMapping("/plant")
@Tag(name = "Plant")
@RequiredArgsConstructor
public class PlantRestController {
    private final PlantRepository plantRepository;
    private final SpeciesRepository speciesRepository;
    private final RoomRepository roomRepository;

    private static final double BRIGHTNESS_SLACK = 0.5;
    private static final double TEMPERATURE_SLACK = 2.0;
    private static final double HUMIDITY_SLACK = 5.0;

    @Operation(summary = "Get all plants of user")
    @ApiResponse(responseCode = "200", description = "List of plants")
    @GetMapping("/all")
    public List<Plant> getAll(@RequestHeader String clientId) {
        return plantRepository.findByClientId(clientId);
    }

    @Operation(summary = "Get plants of user by nickname")
    @ApiResponse(responseCode = "200", description = "List of plants with that nickname (list can be empty)")
    @GetMapping("/nickname/{nickname}")
    public List<Plant> getByNickname(@PathVariable String nickname, @RequestHeader String clientId) {
        return plantRepository.findByClientIdAndNickname(nickname, clientId);
    }

    @Operation(summary = "Get plant")
    @ApiResponse(responseCode = "200", description = "Plant object")
    @ApiResponse(responseCode = "404", description = "Plant with that ID not found", content = @Content)
    @GetMapping("/get/{id}")
    public Plant getPlant(@PathVariable Long id) {
        return plantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Plant " + id + " does not exist"));
    }

    @Operation(summary = "Add plant")
    @ApiResponse(responseCode = "201", description = "Added plant")
    @ApiResponse(responseCode = "400", description = "Invalid plant data", content = @Content)
    @PostMapping("/add")
    public ResponseEntity<Plant> addPlant(@RequestBody NewPlantDTO plantDTO) {
        Room room = roomRepository.findById(plantDTO.getRoomId())
                .orElseThrow(() -> new NotFoundException("Room with ID " + plantDTO.getRoomId() + " does not exist"));
        Species species = speciesRepository.findById(plantDTO.getSpecies())
                .orElseThrow(() -> new NotFoundException("Species " + plantDTO.getSpecies() + " does not exist"));

        Plant plant = Plant.builder()
                .nickname(plantDTO.getNickname())
                .species(species)
                .room(room)
                .build();

        return new ResponseEntity<>(plantRepository.save(plant), HttpStatus.CREATED);
    }

    @Operation(summary = "Rename plant")
    @ApiResponse(responseCode = "200", description = "Plant renamed")
    @ApiResponse(responseCode = "404", description = "Plant with that ID not found", content = @Content)
    @PutMapping("/rename/{id}/{newName}")
    public Plant renamePlant(@PathVariable Long id, @PathVariable String newName) {
        if (!plantRepository.existsById(id))
            throw new NotFoundException("Plant " + id + " does not exist");

        plantRepository.updatePlantName(id, newName);
        return plantRepository.findById(id).orElseThrow();
    }

    @Operation(summary = "Change room of plant")
    @ApiResponse(responseCode = "200", description = "Plant moved into new room")
    @ApiResponse(responseCode = "404", description = "Plant with that ID not found", content = @Content)
    @PutMapping("/change-room/{plantId}/{newRoom}")
    public Plant changeRoom(@PathVariable Long plantId, @PathVariable Long newRoom) {
        if (!plantRepository.existsById(plantId))
            throw new NotFoundException("Plant " + plantId + " does not exist");
        if (!roomRepository.existsById(newRoom)) {
            throw new NotFoundException("Room with ID " + newRoom + " does not exist");
        }
        plantRepository.updatePlantRoom(plantId, newRoom);
        return plantRepository.findById(plantId).orElseThrow();
    }

    @Operation(summary = "Delete plant")
    @ApiResponse(responseCode = "200", description = "Plant deleted")
    @ApiResponse(responseCode = "404", description = "Plant with that ID not found", content = @Content)
    @DeleteMapping("/delete/{id}")
    public Plant deletePlant(@PathVariable Long id) {
        Plant plant = plantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Plant " + id + " does not exist"));
        plantRepository.deleteById(id);
        return plant;
    }

    @Operation(summary = "Get condition")
    @ApiResponse(responseCode = "200", description = "Plant condition object")
    @ApiResponse(responseCode = "404", description = "Plant with that ID not found", content = @Content)
    @GetMapping("/condition/{id}")
    public PlantConditionDTO getCondition(@PathVariable Long id) {
        throw new NotYetImplementedException();
    }

    @Operation(summary = "Get condition over time")
    @ApiResponse(responseCode = "200", description = "List of plant condition objects")
    @ApiResponse(responseCode = "400", description = "Invalid number of days", content = @Content)
    @ApiResponse(responseCode = "404", description = "Plant with that ID not found", content = @Content)
    @GetMapping("/condition/{id}/{days}")
    public List<PlantTimeSeries> getConditionOverTime(@PathVariable Long id, @Positive Integer days) {
        throw new NotYetImplementedException();
    }

    @Operation(summary = "Check if a plant is not in its perfect conditions")
    @ApiResponse(responseCode = "200", description = "List of plants that are not in their perfect conditions. Empty list if all plants are in their perfect conditions")
    @GetMapping("/check-conditions/")
    public List<CheckPlantConditionsDTO> checkConditions(@RequestHeader String clientId) {
        List<CheckPlantConditionsDTO> response = new ArrayList<>();
        for (Plant userPlant : plantRepository.findByClientId(clientId)) {
            PlantConditionDTO condition = getCondition(userPlant.getId());
            Species species = userPlant.getSpecies();

            int brightnessCheck = Utils.checkWithinTargetValue(condition.getBrightness(), species.getPerfectLight(), BRIGHTNESS_SLACK);
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

            int temperatureCheck = Utils.checkWithinTargetValue(condition.getTemperature(), species.getPerfectTemperature(), TEMPERATURE_SLACK);
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

            int humidityCheck = Utils.checkWithinTargetValue(condition.getBrightness(), species.getPerfectLight(), BRIGHTNESS_SLACK);
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
        }
        return response;
    }
    
}